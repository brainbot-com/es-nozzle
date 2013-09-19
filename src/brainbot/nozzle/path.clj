(ns brainbot.nozzle.path
  (:require [clojure.string :as string]))


(defn get-extension-from-basename
  "return lower-cased extension from a basename including the dot '.'"
  [^String s]
  (let [idx (.lastIndexOf s ".")]
    (if (< 0 idx)
      (string/lower-case (subs s idx))
      "")))

(defn- collapse-consecutive-slash
  [s]
  (string/replace s #"/+" "/"))

(defn- trim-slash
  [s]
  (string/replace s #"^/+|/+$" ""))


(defn normalize-path
  [path]
  (let [tmp (-> path
              collapse-consecutive-slash
              trim-slash)]
    (if (= "" tmp)
      "/"
      tmp)))
