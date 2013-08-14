(ns brainbot.nozzle.main
  (:require [clojure.tools.logging :as logging])
  (:require [brainbot.nozzle
             real-fs smb-fs   ;; we need these two for the uberjar
             [fsworker :as fsworker]
             [manage :as manage]
             [extract2 :as extract]
             [esconnect :as esconnect]
             [misc :as misc]]
            [brainbot.nozzle.misc :refer [die]])
  (:require [clojure.tools.cli :as cli])
  (:require [com.brainbot.iniconfig :as ini])
  (:gen-class))


(defn parse-command-line-options
  "parse command line options with clojure.tools.cli
   returns a map of options"

  [args]
  (let [[options args banner]
        (cli/cli args
                 ["-h" "--help" "Show help" :flag true :default false]
                 ;; ["--ampqp-url" "amqp url to connect to"]
                 ;; ["--port" "Port to listen on" :default 5000]
                 ;; ["--root" "Root directory of web server" :default "public"])
                 ["--iniconfig" "(required) ini configuration filename"])]
    (when (:help options)
      (die banner :exit-code 0))
    (when-not (:iniconfig options)
      (die "--iniconfig option missing"))
    (assoc (dissoc options :help) :sections args)))


(declare run-all-sections)

(defn meta-run-section
  [iniconfig section]
  (let [subsections (misc/trimmed-lines-from-string
                     (get-in iniconfig [section "sections"]))]
    (run-all-sections iniconfig subsections)))


(def type->run-section
  {"fsworker" fsworker/worker-run-section
   "meta"   meta-run-section
   "extract"  extract/extract-run-section
   "esconnect" esconnect/esconnect-run-section
   "manage" manage/manage-run-section})


(defn run-all-sections
  [iniconfig sections]
  (doseq [section sections]
    (let [type (get-in iniconfig [section "type"])
          run-section (type->run-section type)]
      (if (nil? run-section)
        nil
        (do
          (logging/info "starting runner for section" section)
          (run-section iniconfig section))))))


(defn -main [& args]
  (let [{:keys [iniconfig sections]} (parse-command-line-options args)]
    (misc/setup-logging!)
    (let [cfg (with-meta (ini/read-ini iniconfig) {:source iniconfig})]
      (logging/debug "using config" cfg)
      (run-all-sections
       cfg
       sections))))
