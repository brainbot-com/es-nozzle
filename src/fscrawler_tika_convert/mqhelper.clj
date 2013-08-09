(ns fscrawler-tika-convert.mqhelper
  (:require [clojure.stacktrace :as trace])
  (:import [com.rabbitmq.client Address ConnectionFactory Connection Channel ShutdownListener])
  (:import [java.util.concurrent Executors])
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [clojure.data.json :as json])
  (:require [fscrawler-tika-convert.routing-key :as rk])

  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]))

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


(defn make-handler
  "create a langohr message handler from a somewhat simpler message
  handler function
  simple-fn will be called with two arguments like

  (simple-fn body {:publish publish})

  where body is the decoded message body and publish is a function

  (fn [command publish-body] ...)"
  [simple-fn]
  (fn [ch {:keys [delivery-tag exchange routing-key] :as meta} ^bytes payload]
    (let [body (json/read-json (String. payload "UTF-8"))
          publish (fn [command publish-body]
                    (lb/publish ch exchange
                                (rk/routing-key-string-with-command routing-key command)
                                (json/write-str publish-body)))]
      (simple-fn body {:publish publish})
      (lb/ack ch delivery-tag))))
