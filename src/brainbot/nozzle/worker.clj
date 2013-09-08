(ns brainbot.nozzle.worker
  (:require [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]))

(defprotocol SectionRunner
  (run-section [this iniconfig section-name] "start runner for section"))

(defn reify-run-section
  [f]
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section-name]
      this)
    SectionRunner
    (run-section [this iniconfig section-name]
      (f iniconfig section-name))))
