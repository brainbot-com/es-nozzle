(ns brainbot.nozzle.rmqstate
  "provide access to rabbitmq queue state via management API"
  (:require [clojure.core.async :refer [go thread chan mult put! close! <! <!! >! >!!] :as async])
  (:require [brainbot.nozzle.routing-key :as rk]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.async-helper :as async-helper]
            [brainbot.nozzle.mqhelper :as mqhelper]
            [brainbot.nozzle.vfs :as vfs])
  (:require [robert.bruce :refer [try-try-again]])
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [clojure.data.json :as json])
  (:require [langohr.http :as rmqapi]
            [langohr.basic :as lb]
            [langohr.core :as rmq]
            [langohr.channel :as lch]))



(defn throw-management-api-error
  "throw a somewhat informative message about missing management rights"
  []
  (throw
   (ex-info
    "no response from RabbitMQ's management API. make sure you have added the management tag for the user in RabbitMQ"
    {:endpoint rmqapi/*endpoint*
     :username rmqapi/*username*})))


(defn list-queues
  "wrapper around langohr's http/list-queues. this one raises an error
  instead of returning nil when the user has no management rights for
  the RabbitMQ management plugin"
  [& args]
  (let [qs (apply rmqapi/list-queues args)]
    (when-not qs
      (throw-management-api-error))
    qs))


(defn start-looping-qwatcher
  "periodically call list-queues and put the result on an async/mult"
  [vhost]
  (let [ch (chan)
        m (mult ch)
        lqfn #(async/thread (list-queues vhost))]
    (assoc (async-helper/looping-go 10000 lqfn ch)
      :mult m)))
