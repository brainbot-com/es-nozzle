(ns brainbot.nozzle.worker
  (:require [clojure.tools.logging :as logging]))

(defprotocol Service
  (start [this])
  (stop [this]))

(defn service-as-string
  [svc]
  (let [m (meta svc)]
    (format "service %s for section %s" (:type m) (:section-name m))))

(defn start-service
  [svc]
  (logging/info "starting" (service-as-string svc))
  (start svc))
