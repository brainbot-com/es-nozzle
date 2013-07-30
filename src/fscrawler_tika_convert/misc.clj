(ns fscrawler-tika-convert.misc
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
