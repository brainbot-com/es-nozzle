(ns brainbot.nozzle.fsworker
  (:require [clojure.tools.logging :as logging])
  (:require brainbot.nozzle.worker)
  (:require [brainbot.nozzle.mqhelper :as mqhelper]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.routing-key :as rk]
            [brainbot.nozzle.sys :as sys])
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  (:require [brainbot.nozzle.vfs :as vfs]))


(defn entry-is-type-ok?
  "return whether entry does not have :error attribute and has the given type"
  [type entry]
  (and
   (not (:error entry))
   (= (get-in entry [:stat :type]) type)))

;; FIXME the following two function defs use a string instead of a
;; symbol.  that works, because values passed in have been
;; deserialized with the json reader.
(def ^{:doc "entry does not have :error set and is a directory"}
  entry-is-directory-ok? (partial entry-is-type-ok? "directory"))
(def ^{:doc "entry does not have :error set and is a file"}
  entry-is-file-ok? (partial entry-is-type-ok? "file"))


(defn assoc-permissions-for-entry
  "associate :permissions with entry"
  [fs directory {relpath :relpath :as entry}]
  (assoc entry "permissions"
         (vfs/get-permissions fs (vfs/join fs [directory relpath]))))

(defn safe-assoc-permissions-for-entry
  "associate :permissions with entry or :error if an exception
  occurs. return nil on 'access denied exceptions'"
  [fs directory entry]
  (try
      (assoc-permissions-for-entry fs directory entry)
      (catch Exception err
        (if (vfs/access-denied-exception? fs err)
          nil
          (assoc entry
            :error (str err))))))

(defn safe-assoc-permissions-for-file-entry
  "associate :permissions with entry passed in, or return entry
  unchanged if it's not a file"
  [fs directory entry]
  (if (entry-is-file-ok? entry)
    (safe-assoc-permissions-for-entry fs directory entry)
    entry))

(defn get-permissions
  "associate :permissions with each file entry in entries and return it"
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
                  :entries (vfs/listdir-and-stat fs path)}
                 (catch Exception err
                   (logging/error "error in listdir" {:path path, :fsid (:fsid fs), :error err})
                   nil))]
    (publish "get_permissions" arg)))


(def command->msg-handler
  {"listdir" simple-listdir
   "get_permissions" simple-get_permissions})

(let [all-commands ["listdir" "get_permissions" "update_directory" "import_file" "extract_content"]]
  (defn- init-all-rmq-structures
    "create all needed rabbitmq structures for the given filesystems"
    [ch rmq-prefix filesystems]
    (doseq [fs filesystems
            c all-commands]
      (mqhelper/initialize-rabbitmq-structures ch c rmq-prefix fs))))

(defn build-handle-connection
  [filesystems rmq-prefix num-workers]
  (fn [conn]
    (logging/info "initializing connection")
    (let [ch (lch/open conn)]
      (init-all-rmq-structures ch rmq-prefix (map :fsid filesystems))
      (lch/close ch))
    (doseq [{:keys [fsid] :as fs} filesystems
            [command handle-msg] (seq command->msg-handler)
            _ (range num-workers)]
      (mqhelper/channel-loop
       conn
       (fn [ch]
         (let [qname (rk/routing-key-string rmq-prefix fsid command)]
           ;; (logging/info "starting consumer for" qname)
           ;; (lb/qos ch 1)
           (lcons/subscribe ch qname (mqhelper/make-handler (partial handle-msg fs)))))))))


(defrecord FSWorkerService [rmq-settings rmq-prefix filesystems num-workers thread-pool]
  worker/Service
  (start [this]
    (future (mqhelper/connect-loop-with-thread-pool
             rmq-settings
             (build-handle-connection filesystems rmq-prefix num-workers)
             thread-pool))))

(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this system section]
      (let [rmq-settings (-> system :config :rmq-settings)
            rmq-prefix (-> system :config :rmq-prefix)
            num-workers (Integer. (get-in system [:iniconfig section "num-workers"] "10"))
            filesystems (map (fn [name] (vfs/make-filesystem system name))
                             (sys/get-filesystems-for-section system section))]
        (->FSWorkerService rmq-settings rmq-prefix filesystems num-workers (:thread-pool system))))))
