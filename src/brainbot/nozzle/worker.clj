(ns brainbot.nozzle.worker)

(defprotocol SectionRunner
  (run-section [this iniconfig section-name] "start runner for section"))

(defn reify-run-section
  [f]
  (reify
    brainbot.nozzle.dynaload/Loadable
    brainbot.nozzle.worker/SectionRunner
    (run-section [this iniconfig section-name]
      (f iniconfig section-name))))
