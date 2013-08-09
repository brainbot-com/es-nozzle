(ns fscrawler-tika-convert.fsworker
  (:gen-class)
  (:require [clojure.tools.logging :as logging]
            [clj-logging-config.log4j :as log-config])
  (:require [fscrawler-tika-convert.reap :as reap]
            [fscrawler-tika-convert.mqhelper :as mqhelper]
            [fscrawler-tika-convert.routing-key :as rk]
            [fscrawler-tika-convert.misc :as misc])
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  ;; (:require [me.raynes.fs :as fs])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as string])
  (:require [com.brainbot.stat :as stat]
            [com.brainbot.vfs :as vfs])
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


(defn handle-msg-get_permissions
  [fs ch {:keys [delivery-tag exchange routing-key] :as meta} ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        directory (:directory body)
        entries-with-permissions (get-permissions fs directory (:entries body))]

    (let [listdir-cmd (rk/routing-key-string-with-command routing-key "listdir")]
      (doseq [entry (filter (fn [entry]
                              (and (entry-is-directory? entry)
                                   (not (:error entry))))
                            entries-with-permissions)]
        (let [subdirectory-path (vfs/join fs [directory (:relpath entry)])]
          (println "publish listdir" subdirectory-path listdir-cmd)
          (lb/publish ch exchange listdir-cmd
                      (json/write-str {:path subdirectory-path})))))


    (lb/publish ch exchange (rk/routing-key-string-with-command routing-key "update_directory")
                (json/write-str (assoc body :entries entries-with-permissions)))

    (lb/ack ch delivery-tag)))


(defn handle-msg-listdir
  [fs ch {:keys [delivery-tag exchange routing-key] :as meta} ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        path (:path body)
        entries (vfs/cmd-listdir fs path)]
    (println "handle-msg-listdir" path)
    (lb/publish ch
                exchange
                (rk/routing-key-string-with-command routing-key "get_permissions")
                (json/write-str {:directory path :entries entries})))
  (lb/ack ch delivery-tag))


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
  {"listdir" handle-msg-listdir
   "get_permissions" handle-msg-get_permissions})


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
           (lcons/subscribe ch qname (partial handle-msg fs))))))))


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
