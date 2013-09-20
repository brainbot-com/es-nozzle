(ns brainbot.nozzle.mqhelper
  (:require [clojure.stacktrace :as trace])
  (:import [com.rabbitmq.client Address ConnectionFactory Connection Channel ShutdownListener])
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [clojure.data.json :as json])
  (:require [brainbot.nozzle.routing-key :as rk])

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
  "connect to rabbitmq with settings rmq-settings and call
  handle-connection with the connection object. if the connection
  fails, wait for 5 seconds and try again. Use thread-pool for
  handling messages"
  [rmq-settings handle-connection thread-pool]
  (connect-loop
   #(connect-with-thread-pool rmq-settings thread-pool)
   handle-connection))


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



(defn initialize-rabbitmq-structures
  "initialize rabbitmq queue and exchange for handling 'command' on
  'filesystem' submitted on 'exchange-name"
  [ch command exchange-name filesystem]
  (le/declare ch exchange-name "topic")
  (let [queue-name (rk/routing-key-string {:id exchange-name
                                           :command command
                                           :filesystem filesystem})]
    (let [queue-state (lq/declare ch queue-name :auto-delete false)]
      (logging/debug "declared queue" (select-keys queue-state [:queue :consumer-count :message-count])))
    (lq/bind ch queue-name exchange-name :routing-key queue-name)
    queue-name))
