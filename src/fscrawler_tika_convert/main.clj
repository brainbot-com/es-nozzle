(ns fscrawler-tika-convert.main
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [fscrawler-tika-convert [reap :as reap] [core :as core] [manage :as manage] [misc :as misc]]
            [fscrawler-tika-convert.misc :refer [die]])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.string :as string])
  (:require [clojure.stacktrace :as trace])
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



(defn die-on-exit-or-error
  "print stack trace and die, used as callback to register-future!"
  [a-future error]
  (when error
    (trace/print-stack-trace error))
  (die "thread died unexpectedly"))


(defn extract-options-from-iniconfig
  [config iniconfig inisection]
  (let [section (or (config inisection)
                    (die (str "section " inisection " missing in " iniconfig)))
        fscrawler-section (or (config "fscrawler") {})
        max-size (Integer. (fscrawler-section "max_size")),
        amqp-url (get fscrawler-section "amqp_url" "amqp://localhost/%2f")
        filesystems (misc/trimmed-lines-from-string (section "filesystems"))]
    (when (zero? (count filesystems))
      (die (str "no filesystems defined in section " inisection " in " iniconfig)))
    {:max-size max-size
     :amqp-url amqp-url
     :filesystems filesystems}))



(defn tika-run-section
  [iniconfig section]
  (let [{:keys [filesystems] :as options} (extract-options-from-iniconfig
                                           iniconfig (:source (meta iniconfig)) section)]
    (reap/start-watching-futures!)

    (doseq [filesystem filesystems]
      (reap/register-future! (future (core/handle-command-for-filesystem-forever filesystem options))
                             die-on-exit-or-error die-on-exit-or-error))))


(declare run-all-sections)

(defn meta-run-section
  [iniconfig section]
  (let [subsections (misc/trimmed-lines-from-string
                     (get-in iniconfig [section "sections"]))]
    (run-all-sections iniconfig subsections)))


(def type->run-section
  {"worker" core/worker-run-section
   "meta"   meta-run-section
   "tika"  tika-run-section
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
