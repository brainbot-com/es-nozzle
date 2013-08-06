(ns fscrawler-tika-convert.core
  (:gen-class)
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [fscrawler-tika-convert.reap :as reap]
            [fscrawler-tika-convert.routing-key :as rk]
            [fscrawler-tika-convert.misc :as misc])
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  ;; (:require [me.raynes.fs :as fs])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as string])
  (:require [com.brainbot.stat :as stat]
            [com.brainbot.vfs :as vfs])
  (:require [clojure.stacktrace :as trace])
  (:require [com.brainbot.iniconfig :as ini])
  (:require [tika])
  (:import java.io.File)
  (:import [com.rabbitmq.client Address ConnectionFactory Connection Channel ShutdownListener])
  (:import [java.util.concurrent Executors]))




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
                       (rk/routing-key-string-with-command routing-key "import_file")
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


(defn new-handle-msg
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (println (format "[consumer] Received a message: %s, delivery tag: %d, content type: %s, type: %s"
                   (String. payload "UTF-8")
                   delivery-tag
                   content-type
                   type))
  (Thread/sleep 1000)
  (println "done")
  (lb/ack ch delivery-tag))



(defn get-permissions-for-entry
  [fs directory {relpath :relpath, {type :type} :stat, error :error :as entry}]
  (if (and (= type "file") (not error))
    (assoc entry "permissions" (vfs/get-permissions fs (vfs/join fs [directory relpath])))
    entry))


(defn get-permissions
  [fs directory entries]
  (map (partial get-permissions-for-entry fs directory)
       entries))

(defn entry-is-type?
  [type entry]
  (= (get-in entry [:stat :type]) type))

(def entry-is-directory? (partial entry-is-type? "directory"))
(def entry-is-file? (partial entry-is-type? "file"))

(defn entry-is-directory?
  [entry]
  (= (get-in entry [:stat :type]) "directory"))


(defn handle-msg-get_permissions
  [fs ch {:keys [delivery-tag exchange routing-key] :as meta} ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        directory (:directory body)
        entries-with-permissions (get-permissions fs directory (:entries body))]

    (let [listdir-cmd (rk/routing-key-string-with-command routing-key "listdir")]
      (doseq [entry (filter (fn [entry]
                              (and (entry-is-directory? entry)
                                   (not (:error entry))))
                            entries-with-permissions)]
        (let [subdirectory-path (vfs/join fs [directory (:relpath entry)])]
          (println "publish listdir" subdirectory-path listdir-cmd)
          (lb/publish ch exchange listdir-cmd
                      (json/write-str {:path subdirectory-path})))))


    (lb/publish ch exchange (rk/routing-key-string-with-command routing-key "update_directory")
                (json/write-str (assoc body :entries entries-with-permissions)))

    (lb/ack ch delivery-tag)))


(defn handle-msg-listdir
  [fs ch {:keys [delivery-tag exchange routing-key] :as meta} ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        path (:path body)
        entries (vfs/cmd-listdir fs path)]
    (println "handle-msg-listdir" path)
    (lb/publish ch
                exchange
                (rk/routing-key-string-with-command routing-key "get_permissions")
                (json/write-str {:directory path :entries entries})))
  (lb/ack ch delivery-tag))


(defn connect-loop
  "connect to rabbitmq with settings rmq-settings and call
  handle-connection with the connection object. if the connection
  fails, wait for 5 seconds and try again"

  [connect handle-connection]
  (while true
    (try
      (let [conn (connect)
            restart-promise (promise)
            sl   (rmq/shutdown-listener
                  (partial deliver restart-promise))]
        (.addShutdownListener conn sl)
        (handle-connection conn)
        (let [cause @restart-promise]
          (println "connection closed" cause (class cause) "restarting in 5s")))
      (catch Exception err
        (trace/print-stack-trace err)
        (println "got exception" err)))
    (Thread/sleep 5000)))




(defn publish-some-message
  [conn]
  (let [ch (lch/open conn)
        queue-name (initialize-rabbitmq-structures ch "extract_content" "nextbot" "fscrawler:test")]
    (doseq [i (range 15)]
      (println "[main] Publishing...")
      (lb/publish ch "nextbot" queue-name "Hello!" :content-type "text/plain" :type "greetings.hi"))
    (lch/close ch)
    queue-name))



(defn connect-with-thread-pool
  [rmq-settings thread-pool]
  (let [cf (#'rmq/create-connection-factory rmq-settings)]
    (.newConnection ^ConnectionFactory cf thread-pool)))


(defn channel-loop
  "create a new channel and call handle-channel on it
   do the same again if the channel is shutdown. this function should
   be used for channel subscribers. handle-channel should not block"

  [conn handle-channel]

  (let [ch (lch/open conn)
        restart (fn [cause]
                  (if-not (lshutdown/initiated-by-application? cause)
                    (logging/error "channel closed" cause)
                    (if-not (lshutdown/hard-error? cause)
                      (future (channel-loop conn handle-channel)))))
        sl (rmq/shutdown-listener restart)]
    (.addShutdownListener ch sl)
    (handle-channel ch)))


(defn break-channel
  [ch delay]
  (println "break channel" ch delay)
  (future
    (Thread/sleep delay)
    (lq/bind ch "no-such-queu-453456546345", "amq.fanout")))


(defn connect-loop-with-thread-pool
  [rmq-settings handle-connection]
  (let [thread-pool (Executors/newFixedThreadPool 500)
        connect (partial connect-with-thread-pool rmq-settings thread-pool)]
    (connect-loop
     connect
     handle-connection)))


(def command->msg-handler
  {"listdir" handle-msg-listdir
   "get_permissions" handle-msg-get_permissions})




(defn build-handle-connection
  [filesystems]
  (fn [conn]
    (logging/info "initializing connection")
    (doseq [{:keys [fsid] :as fs} filesystems
            [command handle-msg] (seq command->msg-handler)]
      (channel-loop
       conn
       (fn [ch]
         (let [qname (initialize-rabbitmq-structures ch command "nextbot" fsid)]
           (logging/info "starting consumer for" qname)
           (lb/qos ch 1)
           (lcons/subscribe ch qname (partial handle-msg fs))))))))

(def default-section-name "fscrawler")


(defn rmq-settings-from-config
  [iniconfig]
  (rmq/settings-from (get-in iniconfig [default-section-name "amqp-url"])))


(defn doit
  []
  (let [iniconfig (ini/read-ini "config.ini")
        section "worker-1"
        rmq-settings (rmq-settings-from-config iniconfig)
        filesystems (vfs/make-filesystems-from-iniconfig iniconfig section)]

    (println "config" iniconfig)
    (println "rmq-settings" rmq-settings)
    (println "fs:" filesystems)
    (when (zero? (count filesystems))
      (misc/die (str "no filesystems defined in section " section)))

    (connect-loop-with-thread-pool
     rmq-settings
     (build-handle-connection filesystems))))


(defn -main [& args]
  (log-config/set-logger!)
  (doit))
