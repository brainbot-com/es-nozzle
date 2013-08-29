(ns brainbot.nozzle.main
  (:require [clojure.tools.logging :as logging])
  (:require [brainbot.nozzle
             [fsworker :as fsworker]
             [manage :as manage]
             [extract2 :as extract]
             [esconnect :as esconnect]
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
      (binding [*out* *err*]
        (println
         (format "Fatal error: You need at least java version 7. The java installation in %s has version %s."
                 (System/getProperty "java.home") java-version))
        (System/exit 1)))))


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


(defn maybe-start-repl-server
  []
  (if-let [port (System/getProperty "nozzle.repl")]
    (do
      (println "starting repl on port" port)
      (nrepl-server/start-server :port (Integer. port)))))


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
    (let [cfg (ini/read-ini iniconfig)]
      (logging/debug "using config" cfg)
      (run-all-sections
       cfg
       sections))))
