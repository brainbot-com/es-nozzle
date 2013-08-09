(ns brainbot.nozzle.fsworker
  (:gen-class)
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [brainbot.nozzle.reap :as reap]
            [brainbot.nozzle.mqhelper :as mqhelper]
            [brainbot.nozzle.routing-key :as rk]
            [brainbot.nozzle.misc :as misc])
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  ;; (:require [me.raynes.fs :as fs])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.string :as string])
  (:require [brainbot.nozzle.stat :as stat]
            [brainbot.nozzle.vfs :as vfs])
  (:require [clojure.stacktrace :as trace])
  (:require [com.brainbot.iniconfig :as ini])
  (:require [tika])
  (:import java.io.File)
  (:import [com.rabbitmq.client Address ConnectionFactory Connection Channel ShutdownListener])
  (:import [java.util.concurrent Executors]))


(defn get-permissions-for-entry
  [fs directory {relpath :relpath, {type :type} :stat, error :error :as entry}]
  (if (and (= type "file") (not error))
    (assoc entry "permissions" (vfs/get-permissions fs (vfs/join fs [directory relpath])))
    entry))


(defn get-permissions
  [fs directory entries]
  (map (partial get-permissions-for-entry fs directory)
       entries))

(defn entry-is-type?
  [type entry]
  (= (get-in entry [:stat :type]) type))

(def entry-is-directory? (partial entry-is-type? "directory"))
(def entry-is-file? (partial entry-is-type? "file"))


(defn simple-get_permissions
  [fs {:keys [directory entries] :as body} {publish :publish}]
  (let [entries-with-permissions (get-permissions fs directory entries)]
    (doseq [entry (filter (fn [entry]
                            (and (entry-is-directory? entry)
                                 (not (:error entry))))
                          entries-with-permissions)]
      (let [subdirectory-path (vfs/join fs [directory (:relpath entry)])]
        (publish "listdir" {:path subdirectory-path})))
    (publish "update_directory" (assoc body :entries entries-with-permissions))))


(defn simple-listdir
  [fs {path :path :as body} {publish :publish}]
  (publish "get_permissions" {:directory path
                              :entries (vfs/cmd-listdir fs path)}))





(defn publish-some-message
  [conn]
  (let [ch (lch/open conn)
        queue-name (misc/initialize-rabbitmq-structures ch "extract_content" "nextbot" "fscrawler:test")]
    (doseq [i (range 15)]
      (println "[main] Publishing...")
      (lb/publish ch "nextbot" queue-name "Hello!" :content-type "text/plain" :type "greetings.hi"))
    (lch/close ch)
    queue-name))


(def command->msg-handler
  {"listdir" simple-listdir
   "get_permissions" simple-get_permissions})


(defn build-handle-connection
  [filesystems]
  (fn [conn]
    (logging/info "initializing connection")
    (doseq [{:keys [fsid] :as fs} filesystems
            [command handle-msg] (seq command->msg-handler)]
      (mqhelper/channel-loop
       conn
       (fn [ch]
         (let [qname (misc/initialize-rabbitmq-structures ch command "nextbot" fsid)]
           (logging/info "starting consumer for" qname)
           (lb/qos ch 1)
           (lcons/subscribe ch qname (mqhelper/make-handler (partial handle-msg fs)))))))))


(defn worker-run-section
  [iniconfig section]
  (let [rmq-settings (misc/rmq-settings-from-config iniconfig)
        filesystems (vfs/make-filesystems-from-iniconfig iniconfig section)]

    (println "config" iniconfig)
    (println "rmq-settings" rmq-settings)
    (println "fs:" filesystems)
    (when (zero? (count filesystems))
      (misc/die (str "no filesystems defined in section " section)))

    (mqhelper/connect-loop-with-thread-pool
      rmq-settings
      (build-handle-connection filesystems))))
