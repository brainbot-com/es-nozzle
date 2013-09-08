(ns brainbot.nozzle.fsfilter
  (:require [clojure.string :as string])
  (:require [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.path :as path]
            [brainbot.nozzle.dynaload :as dynaload]))


(defprotocol RelpathFilter
  (matches-relpath? [this name] ""))

(defn is-dotfile
  [s]
  (= (first s) \.))


(def dotfile
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section-name]
      this)
    RelpathFilter
    (matches-relpath? [this name] (is-dotfile name))))

(defn- normalize-extension
  [s]
  (string/lower-case
   (if (= (first s) \.)
     s
     (str "." s))))

(defn make-has-extension?
  [extensions]
  (let [ext-set (set (map normalize-extension extensions))]
    (fn has-extension [s]
      (contains? ext-set (path/get-extension-from-basename s)))))


;; (def remove-extensions
;;   (reify
;;     dynaload/Loadable
;;     RelpathFilter
;;     (make-remove? [this iniconfig section-name]
;;       (let [extensions (misc/trimmed-lines-from-string (get-in iniconfig [section-name "extensions"]))]
;;         (make-has-extension? extensions)))))
