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
  (:require [clojure.string :as string]
            [clojure.stacktrace :as trace]))

(def number-of-cores
  (.availableProcessors (Runtime/getRuntime)))


(defn remap
  "create a new map from existing map by replacing each value with (f value)"
  [f a-map]
  (into {} (for [[key val] a-map] [key (f val)])))


(defn ensure-endswith-slash
  "append slash to String s if it doesn't already end with a slash"
  [^String s]
  (if (.endsWith s "/")
    s
    (str s "/")))

(defn die
  "print error message msg and exit the program with named argument
 :exit-code or 1"
  [msg & {:keys [exit-code] :or {exit-code 1}}]
  (binding [*out* *err*]
    (println "Error:" msg))
  (System/exit exit-code))


(defn trimmed-lines-from-string
  "split string at newline and return non-empty trimmed lines"
  [^String s]
  (if s
    (->> s
         string/split-lines
         (map string/trim)
         (remove string/blank?))))


(defn setup-logging!
  "configure logging"
  []
  ;; (log-config/set-logger! "org.apache.pdfbox" :pattern "%c %d %p %m%n")
  (doseq [name ["org" "com" "brainbot.nozzle" ""]]
    (log-config/set-logger! name :pattern "%c %d %p %m%n"))

  ;; Jul 01, 2013 4:38:09 PM com.coremedia.iso.boxes.AbstractContainerBox parseChildBoxes

  (doseq [name ["org.apache.pdfbox" "com.coremedia"]]
    (log-config/set-logger! name :level :off)))
