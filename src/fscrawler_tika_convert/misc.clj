(ns fscrawler-tika-convert.misc
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
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
  (doseq [name ["org" "com" "fscrawler-tika-convert" ""]]
    (log-config/set-logger! name :pattern "%c %d %p %m%n"))

  ;; Jul 01, 2013 4:38:09 PM com.coremedia.iso.boxes.AbstractContainerBox parseChildBoxes

  (doseq [name ["org.apache.pdfbox" "com.coremedia"]]
    (log-config/set-logger! name :level :off)))
