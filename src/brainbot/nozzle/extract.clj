(ns brainbot.nozzle.extract
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [brainbot.nozzle.reap :as reap]
            [brainbot.nozzle.routing-key :as rk]
            [brainbot.nozzle.misc :as misc])
  (:require [clojure.stacktrace :as trace])
  (:require [clojure.string :as string])
  (:require [clojure.data.json :as json])
  (:require [tika])
  (:import java.io.File)
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])

  (:require [clojure.stacktrace :as trace])
  (:require [brainbot.nozzle [reap :as reap] [misc :as misc]]
            [brainbot.nozzle.misc :refer [die]]))




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




(defn handle-command-for-filesystem
  [filesystem options]
  (let [rmq-settings (rmq/settings-from (:amqp-url options))
        conn         (do
                       (logging/info "connecting to rabbitmq" (:amqp-url options) rmq-settings)
                       (rmq/connect rmq-settings))
        ch           (lch/open conn)
        queue-name   (misc/initialize-rabbitmq-structures ch "extract_content" "nextbot" filesystem)]
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





(defn die-on-exit-or-error
  "print stack trace and die, used as callback to register-future!"
  [a-future error]
  (when error
    (trace/print-stack-trace error))
  (die "thread died unexpectedly"))


(defn extract-options-from-iniconfig
  [iniconfig section]
  (let [source (:source (meta iniconfig))
        main-section (or (iniconfig misc/main-section-name) {})
        max-size (Integer. (main-section "max-size")),
        amqp-url (get main-section "amqp-url" "amqp://localhost/%2f")
        filesystems (misc/get-filesystems-from-iniconfig iniconfig section)]
    (when (zero? (count filesystems))
      (die (str "no filesystems defined in section " section " in " source)))
    {:max-size max-size
     :amqp-url amqp-url
     :filesystems filesystems}))


(defn extract-run-section
  [iniconfig section]
  (let [{:keys [filesystems] :as options} (extract-options-from-iniconfig
                                           iniconfig section)]
    (reap/start-watching-futures!)

    (doseq [filesystem filesystems]
      (reap/register-future! (future (handle-command-for-filesystem-forever filesystem options))
                             die-on-exit-or-error die-on-exit-or-error))))
