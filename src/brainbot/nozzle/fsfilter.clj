(ns brainbot.nozzle.fsfilter
  (:require [brainbot.nozzle.misc :as misc]))


(defprotocol RelpathFilterBuilder
  (make-remove? [this iniconfig section-name] "build filter"))

(defn is-dotfile
  [s]
  (= (first s) \.))

(def dotfile
  (reify
    brainbot.nozzle.dynaload/Loadable
    RelpathFilterBuilder
    (make-remove? [this iniconfig section-name]
      is-dotfile)))

(defn- trim-dot
  [s]
  (clojure.string/replace s #"^\.+" ""))

(defn make-has-extension?
  [extensions]
  (let [extensions-set (set (map trim-dot extensions))]
    (fn has-extension [s]
      (let [idx (.lastIndexOf s ".")]
        (and (< 0 idx)
             (contains? extensions-set (subs s (inc idx))))))))

(def remove-extensions
  (reify
    brainbot.nozzle.dynaload/Loadable
    RelpathFilterBuilder
    (make-remove? [this iniconfig section-name]
      (let [extensions (misc/trimmed-lines-from-string (get-in iniconfig [section-name "extensions"]))]
        (make-has-extension? extensions)))))
