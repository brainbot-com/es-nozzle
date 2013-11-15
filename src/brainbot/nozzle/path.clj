(ns brainbot.nozzle.path
  (:require [clojure.string :as string]))


(defn get-extension-from-basename
  "return lower-cased extension from a basename including the dot '.'"
  [^String s]
  (let [idx (.lastIndexOf s ".")]
    (if (< 0 idx)
      (string/lower-case (subs s idx))
      "")))


(defn sanitize-extension
  "if an extension is not sane, return emtpy string, otherwise return the extension.
sane means it matches the regular expression [a-z0-9_]{1,6}+$
"
  [^String ext]
  (if (re-find #"\.[a-z0-9_]{1,6}+$" ext)
    ext
    ""))

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
