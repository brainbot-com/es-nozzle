(ns brainbot.nozzle.meta-runner
  (:require [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.dynaload :as dynaload]))

(defrecord MetaRunner [services]
  worker/Service
  (start [this]
    (doseq [svc services]
      (worker/start-service svc))))

(def dynaload-runner
  (comp
   (partial inihelper/ensure-protocol worker/Service)
   inihelper/dynaload-section))

(defn make-meta-runner
  [system sections]
  (->MetaRunner
   (map (fn [section]
          (dynaload-runner system section))
        sections)))

(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this system section]
      (let [sections (misc/trimmed-lines-from-string
                      (get-in system [:iniconfig section "sections"]))]
        (make-meta-runner system sections)))))
