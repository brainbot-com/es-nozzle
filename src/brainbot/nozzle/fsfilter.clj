(ns brainbot.nozzle.fsfilter
  "filter filesystem entries"
  (:require [clojure.string :as string])
  (:require [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.path :as path]
            [brainbot.nozzle.dynaload :as dynaload]))


(defprotocol EntryFilter
  (make-match-entry? [this] "make entry matching function"))

(def make-filter
  (comp
   (partial inihelper/ensure-protocol EntryFilter)
   inihelper/dynaload-section))

(defn is-dotfile
  [{s :relpath}]
  (= (first s) \.))

(defn- reify-simple-filter
  [matches?]
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this system section-name]
      this)
    EntryFilter
    (make-match-entry? [this] matches?)))

;; dotfile act both as an EntryFilter and IniConstructor since it
;; doesn't need any parameters
(def dotfile (reify-simple-filter is-dotfile))

(let [bad-names #{".DS_Store" ".AppleDouble" "__MACOSX"}]
  (defn is-apple-garbage? [{relpath :relpath}]
    (or (contains? bad-names relpath)
        (.startsWith relpath "._"))))

(def apple-garbage (reify-simple-filter is-apple-garbage?))


(defn- normalize-extension
  "normalize extension: convert it to lower-case and make sure it
   starts with a dot '.'"
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
    (make-object-from-section [this {:keys [iniconfig]} section-name]
      (let [extensions (misc/trimmed-lines-from-string (get-in iniconfig [section-name "extensions"]))
            has-extension? (make-has-extension? extensions)]
        (reify
          EntryFilter
          (make-match-entry? [this] has-extension?))))))
