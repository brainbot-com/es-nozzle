(ns brainbot.nozzle.main
  (:import [java.util.concurrent Executors])
  (:require [clojure.tools.logging :as logging])
  (:require [brainbot.nozzle
             [sys :as sys]
             [meta-runner :as meta-runner]
             [dynaload :as dynaload]
             [inihelper :as inihelper]
             [worker :as worker]
             [version :as version]
             [misc :as misc]]
            [brainbot.nozzle.misc :refer [die]])
  (:require [clojure.tools.nrepl.server :as nrepl-server])
  (:require [clojure.tools.cli :as cli])
  (:require [com.brainbot.iniconfig :as ini])
  (:gen-class))



(defn ensure-java-version
  []
  (let [java-version (System/getProperty "java.version")
        [major minor] (map #(Integer. %)
                           (rest (re-find #"^(\d+)\.(\d+)" java-version)))]

    (when (> 0 (compare [major minor] [1 7]))
      (die
       (format "You need at least java version 7. The java installation in %s has version %s."
               (System/getProperty "java.home")
               java-version)))))


(defn parse-command-line-options
  "parse command line options with clojure.tools.cli
   returns a map of options"

  [args]
  (let [[options args banner]
        (cli/cli args
                 ["-h" "--help" "Show help" :flag true :default false]
                 ["--version" "show version" :flag true :default false]
                 ;; ["--ampqp-url" "amqp url to connect to"]
                 ;; ["--port" "Port to listen on" :default 5000]
                 ;; ["--root" "Root directory of web server" :default "public"])
                 ["--iniconfig" "(required) ini configuration filename"])]
    (when (:help options)
      (println banner)
      (System/exit 0))
    (when (:version options)
      (println "nozzle" (version/nozzle-version)
               "on Java" (System/getProperty "java.version")
               (System/getProperty "java.vm.name"))
      (System/exit 0))
    (when-not (:iniconfig options)
      (die "--iniconfig option missing"))
    (assoc (dissoc options :help) :sections args)))


(defn ensure-sections-exist
  [iniconfig sections]
  (let [cfg-sections (set (keys iniconfig))
        sections-set (set sections)
        missing (clojure.set/difference sections-set cfg-sections)]
    (when (seq missing)
      (die (str "the following sections are missing in " (:source (meta iniconfig)) ": "
                (clojure.string/join ", " missing))))))

(defn maybe-start-repl-server
  []
  (when-let [port (System/getProperty "nozzle.repl")]
    (println "starting repl on port" port)
    (nrepl-server/start-server :port (Integer. port))))


(defn sanity-check-tika-resources
  "this function checks that the right tika resources are being used.

tika reads the 'META-INF/services/org.apache.tika.parser.Parser' resource
and uses that to initialize the available parsers.

org.gagravarr/vorbis-java-tika is a dependency of tika and ships with
it's own version of the above file, but it does only list 3
vorbis-java-tika parsers.

lein uberjar chooses to use the resource file from vorbis-java-tika

we must make sure that we do not use the file shipped by
vorbis-java-tika, since then parsing only works for ogg files
"
  []
  (let [path "META-INF/services/org.apache.tika.parser.Parser"
        content (slurp (clojure.java.io/resource path))        
        line-count (count (clojure.string/split content #"\n"))]
    (when (> 20 line-count)
      (throw (ex-info "internal error: broken tika resources" 
                      {:line-count line-count
                       :content content
                       :path path})))))

(defn -main [& args]
  (ensure-java-version)
  (sanity-check-tika-resources)
  (maybe-start-repl-server)
  (let [{:keys [iniconfig sections]} (parse-command-line-options args)]
    (misc/setup-logging!)
    (-> iniconfig
        inihelper/read-ini-with-defaults
        (sys/make-system sections)
        sys/run-system)))
