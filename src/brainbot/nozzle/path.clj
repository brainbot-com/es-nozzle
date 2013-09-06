(ns brainbot.nozzle.path
  (:require [clojure.string :as string]))


(defn get-extension-from-basename
  "return lower-cased extension from a basename including the dot '.'"
  [^String s]
  (let [idx (.lastIndexOf s ".")]
    (if (< 0 idx)
      (string/lower-case (subs s idx))
      "")))
