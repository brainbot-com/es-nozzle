(ns fscrawler-tika-convert.core
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [langohr.basic :as lb]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  ;; (:require [me.raynes.fs :as fs])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as string])
  (:require [com.brainbot.stat :as stat])
  (:require [clojure.stacktrace :as trace])
  (:require [com.brainbot.iniconfig :as ini])
  (:require [tika])
  (:import java.io.File)
  (:import [java.util.concurrent TimeUnit ScheduledThreadPoolExecutor Callable])
  (:gen-class))


(def ^:private scheduled-executor (ScheduledThreadPoolExecutor. 4))


(defn periodically
  "periodically run function f with a fixed delay of n milliseconds"

  [n f]
  (.scheduleWithFixedDelay scheduled-executor ^Runnable f 0 n TimeUnit/MILLISECONDS))


(def future-map (atom {}))

(defn register-future

  [a-future nackfn]
  (swap! future-map assoc a-future nackfn))

(defn unregister-future
  [a-future]
  (swap! future-map dissoc a-future))


(defn deref-exception
  [a-future]
  (try
    (do (deref a-future)
        nil)
    (catch Throwable err
      err)))

(let [call-counter (atom 0)]
  (defn reap-futures
    []
    (swap! call-counter inc)
    (when (zero? (rem @call-counter 240))
      (logging/info "reaping futures" @call-counter))
    (try
      (doseq [[a-future val] @future-map]
        (when (realized? a-future)
          (if-let [error (deref-exception a-future)]
            (do
              (logging/error "************* error in future" (str a-future) error val)
              ((:cleanup-on-error val))))
          (swap! future-map dissoc a-future)))
      (catch Throwable err
        (logging/error "error occured in reap-futures" err)))))


(defn map-from-routing-key-string
  "build map from routing key"
  [rk-string]
  (zipmap [:id :command :filesystem] (string/split rk-string #"\.")))


(defn routing-key-string-from-map
  "build routing key string from map"
  [m]
  (string/join "." [(:id m) (:command m) (:filesystem m)]))


(defn routing-key-string-with-command
  "replace command part of routing key string with command"
  [rk-string command]
  (routing-key-string-from-map (assoc (map-from-routing-key-string rk-string) :command command)))


(def number-of-cores
  (.availableProcessors (Runtime/getRuntime)))


(defn wash
  "remove unicode 0xfffd character from string
  this is the unicode 'replacement character', which tika uses for
  unknown characters"
  [str]
  (string/trim (string/replace (or str "") (char 0xfffd) \space)))


(defn convert
  "convert file and call wash on text property"
  [filename]
  (update-in (tika/parse filename) [:text] wash))

(defn break-channel
  [ch delay]
  (future
    (Thread/sleep delay)
    (lq/bind ch "no-such-queu-453456546345", "amq.fanout")))


(defn handle-message
  [ch metadata ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        ;; {:keys [directory relpath] body}
        routing-key (:routing-key metadata)
        exchange (:exchange metadata)
        directory (:directory body)
        relpath (:relpath (:entry body))
        delivery-tag (:delivery-tag metadata)
        fp (string/join File/separator [directory relpath])
        nack-this-message #(lb/nack ch delivery-tag false false)
        ]
        ;;
    (register-future
     (future
       (try
         (let [converted (convert fp)]
           (lb/publish ch
                       exchange
                       (routing-key-string-with-command routing-key "import_file")
                       (json/write-str (assoc body "tika-content" converted)))
           (lb/ack ch delivery-tag))
         (catch Exception err
           (logging/info "got exception while handling" fp err)
           (trace/print-stack-trace err)
           (lb/nack ch delivery-tag false false))))
     {:cleanup-on-error nack-this-message
      :fp fp})
    (logging/debug "scheduled" fp " ****" )))


(defn initialize-rabbitmq-structures
  "initialize rabbitmq queue and exchange for handling 'command' on
  'filesystem' submitted on 'exchange-name"
  [ch command exchange-name filesystem]
  (le/declare ch exchange-name "topic")
  (let [queue-name (format "%s.%s.%s" exchange-name command filesystem)]
    (let [queue-state (lq/declare ch queue-name :auto-delete false)]
      (logging/info "declared queue" (select-keys queue-state [:queue :consumer-count :message-count])))
    (lq/bind ch queue-name exchange-name :routing-key queue-name)
    queue-name))

(defn run-with-connection
  [filesystem]
  (let [conn       (rmq/connect)
        ch         (lch/open conn)
        queue-name (initialize-rabbitmq-structures ch "extract_content" "nextbot" filesystem)]

    ;; (lq/declare ch queue-name :exclusive false :auto-delete true)
    ;; (lq/bind    ch queue-name "nextbot")
    (lb/qos ch (+ number-of-cores 4))
    ;; (lcons/subscribe ch queue-name handle-message
    ;;                  :auto-ack false))
    ;;                  :handle-shutdown-signal-fn
    ;;                  (fn [consumer_tag reason]
    ;;                    (println "shutdown" consumer_tag reason))

    ;; (break-channel ch 2000)
    (lcons/blocking-subscribe ch queue-name handle-message :auto-ack false)))

(defn die
  [msg exitcode]
  (println "Error:" msg)
  (System/exit exitcode))


(defn setup-logging!
  []
  ;; (log-config/set-logger! "org.apache.pdfbox" :pattern "%c %d %p %m%n")
  (doseq [name ["org" "com" "fscrawler-tika-convert.core" ""]]
    (log-config/set-logger! name :pattern "%c %d %p %m%n"))

  ;; Jul 01, 2013 4:38:09 PM com.coremedia.iso.boxes.AbstractContainerBox parseChildBoxes

  (doseq [name ["org.apache.pdfbox" "com.coremedia"]]
    (log-config/set-logger! name :level :off))

  #_(convert "/home/ralf/t/seven-languages-in-seven-weeks_p4_0.pdf"))


(defn parse-command-line-options
  [args]
  (let [[options args banner]
        (cli/cli args
                 ["-h" "--help" "Show help" :flag true :default false]
                 ;; ["--ampqp-url" "amqp url to connect to"]
                 ;; ["--port" "Port to listen on" :default 5000]
                 ;; ["--root" "Root directory of web server" :default "public"])
                 ["--inisection" "(required) section to use from configuration file"]
                 ["--iniconfig" "(required) ini configuration filename"])]
    (when (:help options)
      (die banner 0))
    (when-not (:iniconfig options)
      (die "--iniconfig option missing" 1))
    (when-not (:inisection options)
      (die "--inisection option missing" 1))
    options))


(defn handle-command-for-filesystem
  [filesystem]
  (while true
    (try
      (run-with-connection filesystem)
      (catch Exception err
        (logging/error "got exception" err)
        (trace/print-stack-trace err)
        (Thread/sleep 5000)
        (logging/info "restarting connection to rabbitmq")))))


(defn trimmed-lines-from-string
  "split string at newline and return trimmed lines"
  [s]
  (if (nil? s)
    nil
    (filter #(not (string/blank? %))
            (map string/trim (string/split-lines s)))))


(defn -main [& args]
  ;; work around dangerous default behaviour in Clojure
  ;; (alter-var-root #'*read-eval* (constantly false))

  (setup-logging!)

  (let [{:keys [iniconfig inisection]} (parse-command-line-options args)
        config (do
                 (logging/info "using section" inisection
                               "from ini file" iniconfig)
                 (ini/read-ini iniconfig))
        section (or (config inisection)
                    (die (str "section " inisection " missing in " iniconfig) 1))
        filesystems (trimmed-lines-from-string (section "filesystems"))]
    (when (zero? (count filesystems))
      (die (str "no filesystems defined in section " inisection " in " iniconfig) 1))

    (periodically 250 reap-futures)

    (let [futures (doall (for [filesystem filesystems]
                           (future (handle-command-for-filesystem filesystem))))]
      (doseq [f futures]
        (try
          @f
          (catch Throwable err
            (trace/print-stack-trace err)
            (die (str "thread unexpectedly died with error " err) 1)))))))
