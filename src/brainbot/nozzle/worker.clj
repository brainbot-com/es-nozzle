(ns brainbot.nozzle.worker
  (:require [brainbot.nozzle.dynaload :as dynaload]))

(defprotocol SectionRunner
  (run-section [this iniconfig section-name] "start runner for section"))

(defn reify-run-section
  [f]
  (reify
    dynaload/Loadable
    SectionRunner
    (run-section [this iniconfig section-name]
      (f iniconfig section-name))))
