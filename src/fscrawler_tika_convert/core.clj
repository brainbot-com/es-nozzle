(ns fscrawler-tika-convert.core
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [fscrawler-tika-convert.reap :as reap])
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
  (:import [java.util.concurrent TimeUnit ScheduledThreadPoolExecutor Callable]))


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


(defn handle-message
  [options ch metadata ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        ;; {:keys [directory relpath] body}
        routing-key (:routing-key metadata)
        exchange (:exchange metadata)
        directory (:directory body)
        size (get-in body [:entry :stat :st_size])
        relpath (:relpath (:entry body))
        delivery-tag (:delivery-tag metadata)
        fp (string/join File/separator [directory relpath])
        on-error (fn [a-future err]
                   (lb/nack ch delivery-tag false false)
                   (trace/print-stack-trace err))]
    (reap/register-future!
     (future
       (try
         (let [converted (if (< size (:max-size options))
                           (convert fp)
                           (do
                             (logging/info "skipping content-extraction, file too large" fp)
                             nil))
               new-body (if converted
                          (assoc body "tika-content" converted)
                          body)]
           (lb/publish ch
                       exchange
                       (routing-key-string-with-command routing-key "import_file")
                       (json/write-str new-body))
           (lb/ack ch delivery-tag))
         (catch Exception err
           (logging/info "got exception while handling" fp err)
           (trace/print-stack-trace err)
           (lb/nack ch delivery-tag false false))))
     on-error nil)
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


(defn handle-command-for-filesystem
  [filesystem options]
  (let [rmq-settings (rmq/settings-from (:amqp-url options))
        conn         (do
                       (logging/info "connecting to rabbitmq" (:amqp-url options) rmq-settings)
                       (rmq/connect rmq-settings))
        ch           (lch/open conn)
        queue-name   (initialize-rabbitmq-structures ch "extract_content" "nextbot" filesystem)]
    (lb/qos ch (+ number-of-cores 4))
    (lcons/blocking-subscribe ch queue-name (partial handle-message options) :auto-ack false)))


(defn run-forever
  "run function forever, i.e. run function, catch exception, wait a
 bit and restart it"
  [sleeptime function & args]
  (while true
    (try
      (apply function args)
      (catch Throwable err
        (logging/error "got exception" err "while running" function "with" args)
        (trace/print-stack-trace err)
        (Thread/sleep sleeptime)))))


(defn handle-command-for-filesystem-forever
  [filesystem options]
  (run-forever 5000 handle-command-for-filesystem filesystem options))
