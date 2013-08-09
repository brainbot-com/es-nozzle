(ns brainbot.nozzle.misc
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  (:require [clojure.string :as string]))


(defn die
  "print error message msg and exit the program with named argument
 :exit-code or 1"
  [msg & {:keys [exit-code] :or {exit-code 1}}]
  (println "Error:" msg)
  (System/exit exit-code))


(defn trimmed-lines-from-string
  "split string at newline and return non-empty trimmed lines"
  [s]
  (if s
    (remove string/blank?
            (map string/trim (string/split-lines s)))))


(defn setup-logging!
  "configure logging"
  []
  ;; (log-config/set-logger! "org.apache.pdfbox" :pattern "%c %d %p %m%n")
  (doseq [name ["org" "com" "brainbot.nozzle" ""]]
    (log-config/set-logger! name :pattern "%c %d %p %m%n"))

  ;; Jul 01, 2013 4:38:09 PM com.coremedia.iso.boxes.AbstractContainerBox parseChildBoxes

  (doseq [name ["org.apache.pdfbox" "com.coremedia"]]
    (log-config/set-logger! name :level :off)))


(def main-section-name "nozzle")

(defn get-filesystems-from-iniconfig
  [iniconfig section]
  (trimmed-lines-from-string
   (or (get-in iniconfig [section "filesystems"])
       (get-in iniconfig [main-section-name "filesystems"]))))


(defn rmq-settings-from-config
  [iniconfig]
  (rmq/settings-from (get-in iniconfig [main-section-name "amqp-url"])))


(defn initialize-rabbitmq-structures
  "initialize rabbitmq queue and exchange for handling 'command' on
  'filesystem' submitted on 'exchange-name"
  [ch command exchange-name filesystem]
  (le/declare ch exchange-name "topic")
  (let [queue-name (format "%s.%s.%s" exchange-name command filesystem)]
    (let [queue-state (lq/declare ch queue-name :auto-delete false)]
      (logging/debug "declared queue" (select-keys queue-state [:queue :consumer-count :message-count])))
    (lq/bind ch queue-name exchange-name :routing-key queue-name)
    queue-name))
