(ns brainbot.nozzle.fsfilter
  (:require [clojure.string :as string])
  (:require [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.path :as path]
            [brainbot.nozzle.dynaload :as dynaload]))


(defprotocol EntryFilter
  (make-match-entry? [this] "make entry matching function"))

(def make-filter-from-iniconfig
  (comp
   (partial inihelper/ensure-protocol EntryFilter)
   inihelper/dynaload-section))

(defn is-dotfile
  [{s :relpath}]
  (= (first s) \.))


(def dotfile
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section-name]
      this)
    EntryFilter
    (make-match-entry? [this] is-dotfile)))


(defn- normalize-extension
  [s]
  (string/lower-case
   (if (= (first s) \.)
     s
     (str "." s))))

(defn make-has-extension?
  [extensions]
  (let [ext-set (set (map normalize-extension extensions))]
    (fn has-extension [{s :relpath :as entry}]
      (and (= (get-in entry [:stat :type]) :file)
           (contains? ext-set (path/get-extension-from-basename s))))))

(def extensions-filter-constructor
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section-name]
      (let [extensions (misc/trimmed-lines-from-string (get-in iniconfig [section-name "extensions"]))
            has-extension? (make-has-extension? extensions)]
        (reify
          EntryFilter
          (make-match-entry? [this] has-extension?))))))
