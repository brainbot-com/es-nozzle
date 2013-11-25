(ns brainbot.nozzle.esconnect
  "this namespace provides functionality to connect with the
elasticsearch backend. the import_file and update_directory
subcommands of the esconnect worker types are implemented here"
  (:require [clojure.pprint :refer [pprint]])
  (:require [clojure.string :as string])
  (:require [clojure.tools.logging :as logging])
  (:require [langohr.core :as rmq]
            [langohr.consumers :as lcons]
            [langohr.basic :as lb])
  (:require [clj-time.core :as tcore]
            [clj-time.coerce :as tcoerce])
  (:require [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.path :as path]
            [brainbot.nozzle.vfs :as vfs]
            [brainbot.nozzle.sys :as sys]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.mqhelper :as mqhelper])
  (:require [robert.bruce :refer [try-try-again]])
  (:require [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]))

(def ^:private token-document-null-value "NOBODY")

;; (esr/connect! "http://127.0.0.1:9200")
(let [parent {:index "not_analyzed", :type "string", :store "yes"}
      token {:index "not_analyzed",
             :type "string",
             :store true,
             :null_value token-document-null-value}]
  (def mapping-types
    {"doc" {:_all {:enabled false},
            :_source {:enabled true},
            :_index {:enabled true},
            :properties
            {:parent parent
             :tags {:index "not_analyzed",
                    :type "string",
                    :index_options "docs",
                    :store true,
                    :omit_norms true},
             :lastmodified {:type "date", :store "yes"},
             :size {:type "long", :store "yes"},
             :content {:type "string", :store "yes"},
             :content_type {:type "string", :store "yes" :index "not_analyzed"},
             :extension {:type "string", :store "yes" :index "not_analyzed"},
             :title   {:type "string", :store "yes"},
             :deny_token_document token,
             :allow_token_document token}},
     "dir" {:_all {:enabled false},
            :_source {:enabled true}
            :properties
            {:lastmodified {:type "date", :store "yes"},
             :parent parent}}}))

;;; when sending around messages via rabbitmq we pass the lastmodified
;;; date as unix time (as integer). the field is called mtime while in
;;; rabbitmq. elasticsearch has a dedicated date type, and we store
;;; the lastmodified date under the lastmodified field.
;;; mtime->lastmodied and lastmodified->mtime convert between these
;;; two representations.

(defn mtime->lastmodified
  "convert unix timestamp to elasticsearch compatible string representation"
  [mtime]
  (-> mtime long (* 1000) tcoerce/from-long str))

(defn lastmodified->mtime
  "convert elasticsearch date string to unix timestamp"
  [lastmodified]
  (-> lastmodified tcoerce/from-string tcoerce/to-long (quot 1000)))

(defn strip-mime-type-parameters
  "strip mime type parameters from mime type string"
  [s]
  (-> s (string/split #";" 2) first string/trim))


(defn ensure-index-and-mappings
  "create index with name index-name and make sure the mappings in
mapping-types are used. if the index already exists, just update the
mappings"
  [index-name]
  (if (esi/exists? index-name)
    (doseq [[doctype mapping] mapping-types]
      ;; (println "update-mapping" doctype mapping)
      (esi/update-mapping index-name doctype :mapping {:mapping mapping}))
    (esi/create index-name :mappings mapping-types)))


(defn es-listdir
  "list contents of directory 'parent' in index 'index-name'"
  [index-name parent]
  (esd/search index-name
              ["dir" "doc"]
              :size 1000000
              :query {:match_all {}}
              :fields ["parent" "lastmodified" "allow_token_document" "deny_token_document" "size"]
              :filter {:term {:parent parent}}))


(let [estype->type {"dir" "directory"
                    "doc" "file"}]
  (defn- convert-es-entry
    [{:keys [_type _id fields]}]
    {:id _id
     :type (estype->type _type)
     :allow (:allow_token_document fields)
     :deny (:deny_token_document fields)
     :size (:size fields)
     :mtime (-> fields :lastmodified lastmodified->mtime)}))

(defn enrich-es-entries
  "convert entries from es-listdir to our common format"
  [entries]
  (loop [entries entries]
    (if (map? entries)
      (recur (:hits entries))
      (map convert-es-entry entries))))


(defn es-recursive-delete
  "recursively delete directory 'parent' from index 'index-name'"
  [index-name parent]
  (let [with-slash (misc/ensure-endswith-slash parent)]
    (esd/delete-by-query-across-all-types
     index-name
     {:prefix {:_id with-slash}})
    (esd/delete index-name "dir" parent)))



(defn make-id
  "build an id suitable for use in elasticsearch. we just join the
  components with / as separator and make sure that multiple slashes
  are replaced with one slash"
  [& args]
  (string/replace (string/join "/" args) #"/+" "/"))

(defn enrich-mq-entries
  "convert entries received via rabbitmq to our common format"
  [directory entries]
  (map
   (fn [entry]
     (assoc entry
       :id (make-id "" directory (:relpath entry))
       :type (if (:error entry)
               "error"
               (get-in entry [:stat :type]))
       :mtime (get-in entry [:stat :mtime])
       :size (get-in entry [:stat :size])))
   entries))

(defn make-id-map
  "build a hashmap, mapping the :id of each entry to the entry itself"
  [entries]
  (apply hash-map (mapcat (juxt :id identity) entries)))

(defn find-missing-entries
  "return a seq of entries missing in existing-map, uses :id of each
entry for lookup in existing-map"
 [existing-map entries]
  (remove #(contains? existing-map (:id %)) entries))

(declare simplify-permissions-for-es)

(defn permset
  "create a set. elasticsearch may give us a single string, handle that case"
  [p]
  (if (string? p)
    #{p}
    (set p)))

(defn entry-needs-update?
  "compare es-entry with mq-entry and determine if we need to update it"
  [es-entry mq-entry]
  (or (not= (:mtime es-entry) (:mtime mq-entry))
      (not= (:size es-entry) (:size mq-entry))
      (let [mqperm (-> mq-entry :permissions simplify-permissions-for-es)
            mqperm* (misc/remap permset mqperm)
            esperm (select-keys es-entry [:allow :deny])
            esperm* (misc/remap permset esperm)]
        (not= mqperm* esperm*))))

(defn find-updates
  "compare entries with those in es-file-map and return a seq of
   entries that need to be updated"
  [es-file-map entries]
  (remove
   nil?
   (map (fn [mq-entry]
          (if-let [es-entry (-> mq-entry :id es-file-map)]
            (when (entry-needs-update? es-entry mq-entry)
              mq-entry)))
        entries)))

(defn compare-directories
  "compares listing of two directories and creates 'instructions' on
how to update the second directory to match the first one"
  [mq-entries es-entries]
  (let [mq-entries-by-type (group-by :type mq-entries)
        mq-directory-map (make-id-map (mq-entries-by-type "directory"))
        mq-file-map (make-id-map (mq-entries-by-type "file"))

        es-entries-by-type (group-by :type
                                     (find-missing-entries
                                      (make-id-map (mq-entries-by-type "error"))
                                      es-entries))
        es-directory-map (make-id-map (es-entries-by-type "directory"))
        es-file-map (make-id-map (es-entries-by-type "file"))]
    {:delete-directories (find-missing-entries mq-directory-map (es-entries-by-type "directory"))
     :delete-files (find-missing-entries mq-file-map (es-entries-by-type "file"))
     :create-directories (find-missing-entries es-directory-map (mq-entries-by-type "directory"))
     :create-files (find-missing-entries es-file-map (mq-entries-by-type "file"))
     :update-files (find-updates es-file-map (mq-entries-by-type "file"))}))


(defn apply-diff-to-elasticsearch
  [{:keys [delete-directories delete-files create-directories]} es-index parent-id]
  (doseq [e delete-directories]
    (es-recursive-delete es-index (:id e)))
  (doseq [e delete-files]
    (esd/delete es-index "doc" (:id e)))
  (doseq [e create-directories]
    (esd/put es-index "dir"
             (:id e)
             {:lastmodified (mtime->lastmodified (:mtime e))
              :parent parent-id})))

(let [default-value (list token-document-null-value)]
  (defn simplify-permissions-for-es
    [permissions]
    (let [allow->sids (misc/remap #(sort (set (map :sid %)))
                                  (group-by :allow permissions))]
      {:allow (allow->sids true default-value)
       :deny  (allow->sids false default-value)})))

(defn get-tags-from-path
  [s]
  (sort (disj (set (string/split s #"/")) "")))


(defn simple-import_file
  [{es-index :index :as fs} {:keys [directory entry] :as body} {publish :publish}]
  (let [parent-id (make-id "" directory)
        relpath (:relpath entry)
        extension (path/get-extension-from-basename relpath)
        extension* (if (:sanitize-extensions fs)
                     (path/sanitize-extension extension)
                     extension)
        id (make-id "" directory relpath)
        title (or (get-in body [:extract :tika-content :dc:title])
                  relpath)
        simple-perms (simplify-permissions-for-es (:permissions entry))]


    (esd/put es-index "doc"
             id
             {:parent parent-id
              :content (get-in body [:extract :tika-content :text])
              :extension extension*
              :content_type (strip-mime-type-parameters
                             (or
                              (first (get-in body [:extract :tika-content :content-type]))
                              ""))

              :title title
              :tags (get-tags-from-path directory)
              :allow_token_document (simple-perms :allow)
              :deny_token_document (simple-perms :deny)
              :lastmodified (mtime->lastmodified (get-in entry [:stat :mtime]))
              :size (get-in entry [:stat :size])})))



(defn simple-update_directory
  [{es-index :index :as fs} {:keys [directory entries] :as body} {publish :publish}]
  ;; (logging/info "simple-update" directory)
  (let [parent-id (make-id "" directory)
        es-entries (enrich-es-entries (es-listdir es-index parent-id))
        mq-entries (enrich-mq-entries directory entries)
        diff (compare-directories mq-entries es-entries)]
    (apply-diff-to-elasticsearch diff es-index parent-id)

    (doseq [e (concat (:create-files diff) (:update-files diff))]
      (publish "extract_content"
               {:directory directory
                :entry (select-keys e [:relpath :permissions :stat])}))))


(def command->msg-handler
  {"update_directory" simple-update_directory
   "import_file"      simple-import_file})

(defn build-handle-connection
  [filesystems num-workers rmq-prefix]
  (fn [conn]
    (logging/info "initializing rabbitmq connection with" num-workers "workers")
    (doseq [_ (range num-workers)]
      (mqhelper/channel-loop
       conn
       (fn [ch]
         (doseq [fs filesystems
                 [command handle-msg] (seq command->msg-handler)]
           (let [qname (mqhelper/initialize-rabbitmq-structures ch command rmq-prefix (:fsid fs))]
             ;; (lb/qos ch 1)
             (lcons/subscribe ch qname
                              (mqhelper/make-handler (partial handle-msg fs))))))))))


(defn indexes-from-filesystems
  [filesystems]
  (distinct (map :index filesystems)))


(defn ensure-all-indexes-and-mappings
  [filesystems]
  (doseq [idx (indexes-from-filesystems filesystems)]
    (ensure-index-and-mappings idx)))

(defn initialize-elasticsearch
  [es-url filesystems]
  (esr/connect! es-url)
  (ensure-all-indexes-and-mappings filesystems))


(defrecord ESConnectService [rmq-settings rmq-prefix num-workers filesystems es-url thread-pool]
  worker/Service
  (start [this]
    (try-try-again {:tries :unlimited
                    :error-hook (fn [err]
                                  (logging/error "error while initializing elasticsearch connection and indexes" es-url err))}
                   #(initialize-elasticsearch es-url filesystems))

    (mqhelper/connect-loop-with-thread-pool
     rmq-settings
     (build-handle-connection filesystems num-workers rmq-prefix)
     thread-pool)))


(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this system section]
      (let [iniconfig (:iniconfig system)
            rmq-settings (-> system :config :rmq-settings)
            rmq-prefix (-> system :config :rmq-prefix)
            num-workers (Integer. (get-in iniconfig [section "num-workers"] "10"))
            filesystem-names (sys/get-filesystems-for-section system section)
            filesystems (map #(vfs/make-additional-fs-map system %) filesystem-names)
            es-url (-> system :config :es-url)]
        (->ESConnectService rmq-settings
                            rmq-prefix
                            num-workers
                            filesystems
                            es-url
                            (:thread-pool system))))))
