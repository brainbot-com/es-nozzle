(ns fscrawler-tika-convert.main
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [fscrawler-tika-convert.reap :as reap]
            [fscrawler-tika-convert.core :as core])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.string :as string])
  (:require [clojure.stacktrace :as trace])
  (:require [com.brainbot.iniconfig :as ini])
  (:gen-class))


(defn die
  "print error message msg and exit the program with named argument
 :exit-code or 1"
  [msg & {:keys [exit-code] :or {exit-code 1}}]
  (println "Error:" msg)
  (System/exit exit-code))


(defn setup-logging!
  "configure logging"
  []
  ;; (log-config/set-logger! "org.apache.pdfbox" :pattern "%c %d %p %m%n")
  (doseq [name ["org" "com" "fscrawler-tika-convert" ""]]
    (log-config/set-logger! name :pattern "%c %d %p %m%n"))

  ;; Jul 01, 2013 4:38:09 PM com.coremedia.iso.boxes.AbstractContainerBox parseChildBoxes

  (doseq [name ["org.apache.pdfbox" "com.coremedia"]]
    (log-config/set-logger! name :level :off))

  #_(convert "/home/ralf/t/seven-languages-in-seven-weeks_p4_0.pdf"))


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
                 ["--inisection" "(required) section to use from configuration file"]
                 ["--iniconfig" "(required) ini configuration filename"])]
    (when (:help options)
      (die banner :exit-code 0))
    (when-not (:iniconfig options)
      (die "--iniconfig option missing"))
    (when-not (:inisection options)
      (die "--inisection option missing"))
    (dissoc options :help)))


(defn trimmed-lines-from-string
  "split string at newline and return non-empty trimmed lines"
  [s]
  (if s
    (remove string/blank?
            (map string/trim (string/split-lines s)))))


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
        filesystems (trimmed-lines-from-string (section "filesystems"))]
    (when (zero? (count filesystems))
      (die (str "no filesystems defined in section " inisection " in " iniconfig)))
    {:max-size max-size
     :amqp-url amqp-url
     :filesystems filesystems}))


(defn -main [& args]
  ;; work around dangerous default behaviour in Clojure
  ;; (alter-var-root #'*read-eval* (constantly false))

  (setup-logging!)

  (let [{:keys [iniconfig inisection]} (parse-command-line-options args)
        config (do
                 (logging/info "using section" inisection
                               "from ini file" iniconfig)
                 (ini/read-ini iniconfig))
        {:keys [filesystems] :as options} (extract-options-from-iniconfig
                                           config iniconfig inisection)]
    (reap/start-watching-futures!)

    (doseq [filesystem filesystems]
      (reap/register-future! (future (core/handle-command-for-filesystem-forever filesystem options))
                             die-on-exit-or-error die-on-exit-or-error)))


  @(promise))
