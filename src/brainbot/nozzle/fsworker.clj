(ns brainbot.nozzle.fsworker
  (:require [clojure.tools.logging :as logging])
  (:require brainbot.nozzle.worker)
  (:require [brainbot.nozzle.mqhelper :as mqhelper]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.misc :as misc])
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  (:require [brainbot.nozzle.vfs :as vfs]))


(defn entry-is-type-ok?
  [type entry]
  (and
   (not (:error entry))
   (= (get-in entry [:stat :type]) type)))

(def entry-is-directory-ok? (partial entry-is-type-ok? "directory"))
(def entry-is-file-ok? (partial entry-is-type-ok? "file"))


(defn assoc-permissions-for-entry
  [fs directory {relpath :relpath :as entry}]
  (assoc entry "permissions"
         (vfs/get-permissions fs (vfs/join fs [directory relpath]))))


(defn safe-assoc-permissions-for-file-entry
  [fs directory entry]
  (if (entry-is-file-ok? entry)
    (try
      (assoc-permissions-for-entry fs directory entry)
      (catch Exception err
        (if (vfs/access-denied-exception? fs err)
          nil
          (assoc entry
            :error (str err)))))
    entry))



(defn get-permissions
  [fs directory entries]
  (remove nil?
          (map
           (partial safe-assoc-permissions-for-file-entry fs directory)
           entries)))


(defn simple-get_permissions
  [fs {:keys [directory entries] :as body} {publish :publish}]
  (let [entries-with-permissions (doall (get-permissions fs directory entries))]
    (doseq [entry (filter entry-is-directory-ok? entries-with-permissions)]
      (let [subdirectory-path (vfs/join fs [directory (:relpath entry)])]
        (publish "listdir" {:path subdirectory-path})))
    (publish "update_directory" (assoc body :entries entries-with-permissions))))


(defn simple-listdir
  [fs {path :path :as body} {publish :publish}]
  (if-let [arg (try
                 {:directory path
                  :entries (vfs/cmd-listdir fs path)}
                 (catch Exception err
                   (logging/error "error in listdir" err)
                   nil))]
    (publish "get_permissions" arg)))


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


(defrecord FSWorkerService [rmq-settings filesystems]
  worker/Service
  (start [this]
    (future (mqhelper/connect-loop-with-thread-pool
             rmq-settings
             (build-handle-connection filesystems)))))

(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section]
      (let [rmq-settings (inihelper/rmq-settings-from-config iniconfig)
            filesystems (vfs/make-filesystems-from-iniconfig iniconfig section)]

        (when (empty? filesystems)
          (misc/die (str "no filesystems defined in section " section)))
        (->FSWorkerService rmq-settings filesystems)))))

