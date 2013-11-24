(ns brainbot.nozzle.sys
  "system map creation and a bit of iniconfig utils"
  (:require [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.rmqstate :as rmqstate]
            [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.meta-runner :as meta-runner])
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [langohr.http :as rmqapi]
            [langohr.basic :as lb]
            [langohr.core :as rmq]
            [langohr.channel :as lch])
  (:import [java.util.concurrent Executors]))

(defn valid-name?
  "check if s is a valid-name, i.e. a non-empty sequence of the
  characters a-zA-Z0-9 - and _"
  [s]
  (boolean (re-matches #"^[-a-zA-Z0-9_]+$" s)))

(defn- parse-main-section* [iniconfig]
  {:rmq-settings (inihelper/rmq-settings-from-config iniconfig)
   :filesystems (misc/trimmed-lines-from-string
                 (get-in iniconfig [inihelper/main-section-name "filesystems"]))
   :rmq-prefix (get-in iniconfig [inihelper/main-section-name "rmq-prefix"] inihelper/main-section-name)
   :es-url (or (get-in iniconfig [inihelper/main-section-name "es-url"])
               "http://localhost:9200")})

(defn- inidie
  "call misc/die, put the section name and iniconfig source in front
  of the error message"
  [iniconfig section msg]
  (misc/die (format "while parsing section %s in %s: %s"
                    section
                    (-> iniconfig meta :source)
                    msg)))

(defn- make-die-fn
  "return fn wrapping misc/die, put the section name and iniconfig
  source in front of the error message"
  [iniconfig section]
  (partial inidie iniconfig section))

(defn- validate-filesystems
  "validate filesystem names. call die with an error message, if validation fails"
  [filesystems die]
  (doseq [fs filesystems]
    (when-not (valid-name? fs)
      (die (format "the filesystem name %s is not valid" (pr-str fs))))))

(defn- validate-main-section
  "validate main section"
  [{:keys [rmq-prefix filesystems]} die]
  (when-not (valid-name? rmq-prefix)
    (die (format "rmq-prefix value %s is not valid" (pr-str rmq-prefix))))
  (validate-filesystems filesystems die))


(defn- parse-main-section
  "parse nozzle's main section. the result will be stored as :config inside the system map"
  [iniconfig]
  (let [res (parse-main-section* iniconfig)
        die (make-die-fn iniconfig inihelper/main-section-name)]
    (validate-main-section res die)
    res))


(defn http-connect!
  "initialize langohr.http by calling its connect! method"
  ([] (http-connect! {}))
  ([{:keys [api-endpoint username password]
      :or {api-endpoint "http://localhost:55672"
           username "guest"
           password "guest"}}]
     (logging/info "using rabbitmq api-endpoint" api-endpoint "as user" username)
     (rmqapi/connect! api-endpoint username password)))


(defn make-system
  "create a system map. the system map is where we store our
  configuration data and some state. the system map contains the followings keys:

  :iniconfig the ini configuration
  :command-sections command sections as passed on the command line
  :config parsed values from the main section
  :thread-pool the thread pool which should be used by workers
  :name->obj an atom, mapping section names as specified in the ini
             config to the objects which we created."
  [iniconfig command-sections]
  (let [config (parse-main-section iniconfig)]
    (http-connect! config) ;; need to set this up for rmqstate/start-looping-qwatcher
    {:iniconfig iniconfig
     :command-sections command-sections
     :looping-qwatcher (rmqstate/start-looping-qwatcher (-> config :rmq-settings :vhost))
     :config config
     :name->obj (atom {})
     :thread-pool (Executors/newFixedThreadPool 256)}))

(def ^{:doc "the system map currently running.  only use this for development"}
  current-system nil)

(defn run-system
  "start running the system by starting a meta-runner with the
  command-sections"
  [{:keys [iniconfig command-sections] :as system}]
  (alter-var-root #'current-system (constantly system))
  (worker/start (meta-runner/make-meta-runner system command-sections)))

(defn- get-filesystems-for-section*
  "get filesystems specified in section and validate them. no fallback"
  [system section-name]
  (let [filesystems (misc/trimmed-lines-from-string
                     (get-in system [:iniconfig section-name "filesystems"]))
        die (make-die-fn (:iniconfig system) section-name)]
    (validate-filesystems filesystems die)
    filesystems))

(defn get-filesystems-for-section
  "get the list of filesystem names specified in the given section or
  as a fallback in the main section"
  [system section-name]
  (or (get-filesystems-for-section* system section-name)
      (get-in system [:config :filesystems])
      (inidie (:iniconfig system) section-name "no filesystems defined")))
