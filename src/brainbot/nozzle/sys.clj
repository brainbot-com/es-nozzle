(ns brainbot.nozzle.sys
  (:require [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.meta-runner :as meta-runner])
  (:import [java.util.concurrent Executors]))

(defn- parse-main-section [iniconfig]
  {:rmq-settings (inihelper/rmq-settings-from-config iniconfig)
   :filesystems (misc/trimmed-lines-from-string
                 (get-in iniconfig [inihelper/main-section-name "filesystems"]))
   :rmq-prefix (get-in iniconfig [inihelper/main-section-name "rmq-prefix"] inihelper/main-section-name)
   :es-url (or (get-in iniconfig [inihelper/main-section-name "es-url"]) "http://localhost:9200")})

(defn make-system [iniconfig command-sections]
  {:iniconfig iniconfig
   :command-sections command-sections
   :config (parse-main-section iniconfig)
   :name->obj (atom {})
   :thread-pool (Executors/newFixedThreadPool 256)})

(def ^{:doc "the system map currently running.  only use this for development"}
  current-system nil)

(defn run-system
  [{:keys [iniconfig command-sections] :as system}]
  (alter-var-root #'current-system (constantly system))
  (worker/start (meta-runner/make-meta-runner system command-sections)))

(defn get-filesystems-for-section
  [system section-name]
  (or (misc/trimmed-lines-from-string (get-in system [:iniconfig section-name "filesystems"]))
      (get-in system [:config :filesystems])
      (misc/die (str "no filesystems defined in section " section-name))))
