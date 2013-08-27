(ns brainbot.nozzle.manage
  (:require [brainbot.nozzle.routing-key :as rk]
            [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.vfs :as vfs])
  (:require [langohr.core :as rmq])

  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [clojure.data.json :as json])

  (:require [com.brainbot.iniconfig :as ini])

  (:require [langohr.http :as rmqapi]
            [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]))


(defn filter-by-vhost
  [vhost coll]
  (filter #(= vhost (:vhost %)) coll))

(defn filter-by-rkmap
  [rkmap coll]
  (filter (fn [{:keys [name]}]
            (= rkmap
               (select-keys
                (rk/map-from-routing-key-string name) (keys rkmap))))
          coll))

(defn num-messages-from-queue-state
  [queue-state vhost rkmap]
  (reduce + (map :messages (filter-by-rkmap rkmap (filter-by-vhost vhost queue-state)))))


(defn wait-for-zero-messages
  [get-num-messages]
  (loop  [zero-count 0]
    (if (= 0 (get-num-messages))
      (when (< zero-count 6)
        (Thread/sleep 500)
        (recur (inc zero-count)))
      (do
        (Thread/sleep 10000)
        (recur 0)))))


(defn start-synchronization
  [id filesystem]
  (let [conn (rmq/connect)
        ch (lch/open conn)]
    (misc/initialize-rabbitmq-structures
     ch "listdir" id filesystem)
    (lb/publish ch id
                (rk/routing-key-string-from-map {:id id :filesystem filesystem :command "listdir"})
                (json/write-str {:path "/"}))
    (rmq/close ch)
    (rmq/close conn)))


(defn manage-filesystem
  [id fs]
  (let [qname (rk/routing-key-string-from-map {:id id :filesystem fs :command "*"})
        get-num-messages (fn []
                           (num-messages-from-queue-state
                            (rmqapi/list-queues)
                            "/"
                            {:id id
                             :filesystem fs}))
        wait-idle (partial wait-for-zero-messages get-num-messages)]
    (logging/debug "waiting for" qname "to become idle")
    (wait-idle)
    (while true
      (logging/info "starting synchronization of" qname)
      (start-synchronization id fs)
      (Thread/sleep 10000)
      (wait-idle)
      (logging/info "synchronization of" qname "finished. restarting in 1h")
      (Thread/sleep (* 3600 1000)))))


(defn manage-run-section
  [iniconfig section]
  (let [rmq-settings (misc/rmq-settings-from-config iniconfig)
        filesystems (misc/get-filesystems-from-iniconfig iniconfig section)]
    (when (empty? filesystems)
      (misc/die (str "no filesystems defined in section " section)))
    (doseq [fs filesystems]
      (future (manage-filesystem "nextbot" fs)))))
