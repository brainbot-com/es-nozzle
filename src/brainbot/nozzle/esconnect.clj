(ns brainbot.nozzle.esconnect
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
            [brainbot.nozzle.sys :as sys]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.mqhelper :as mqhelper])
  (:require [robert.bruce :refer [try-try-again]])
  (:require [clojurewerkz.elastisch.rest.document :as esrd]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esri]
            [clojurewerkz.elastisch.native.document :as esnd]
            [clojurewerkz.elastisch.native :as esn]
            [clojurewerkz.elastisch.native.index :as esni])
  (:require [clojure.stacktrace :as trace])
  (:import  [org.elasticsearch.node NodeBuilder Node]))

(def ^:private token-document-null-value "NOBODY")

;; define variable for es client
(def es nil)

;; For automatic testing (and embedded setups) we want to allow the internal client
;; as well as the rest client. Since we use only very few methods of elastisch,
;; we define a protocol abstraction for talking with elasticsearch.
(defprotocol Elasticsearch
  "elasticsearch connector"
  (connect [es] "connect the client")
  (search [es index mapping-types {:keys [size query fields flt] :as options}] "search")
  (put [es index mapping-type id document] "create or update a document")
  (delete-by-query-across-all-types [es ^String index-name query] "delete-by-query operation across all mapping types")
  (delete [es ^String index-name ^String mapping-type id] "delete document by id")
  (exists? [es ^String index-name] "index exists?")
  (update-mapping [es ^String index-name-or-names ^String type-name options] "update a mapping")
  (create [es ^String index-name options] "create an index"))

(defrecord RestElastisch [es-url]
  Elasticsearch 
  (connect [es] 
    (esr/connect! es-url))
  
  (search [es index mapping-types {:keys [size query fields flt] :as options}]
    (esrd/search index mapping-types :size size :query query :fields fields :filter flt))
  
  (put [es index mapping-type id document]     
    (esrd/put index mapping-type id document))
  
  (delete-by-query-across-all-types [es index-name query] 
    (esrd/delete-by-query-across-all-types index-name query))  

  (delete [es index-name mapping-type id]
    (esrd/delete index-name mapping-type id))
  
  (exists? [es index-name]
    (esri/exists? index-name))
  
  (update-mapping [es index-name-or-names type-name {:keys [mapping]}] 
    (esri/update-mapping index-name-or-names type-name :mapping mapping))
  
  (create [es index-name options]
    (esri/create index-name :mappings options)))



(defrecord NativeElastisch [clustername local]
  Elasticsearch 
  (connect [es]
    (if local
      (let [clientnode (.. (NodeBuilder/nodeBuilder) (local true) (clusterName clustername) (node))]
        (esn/connect-to-local-node! clientnode))
      (esn/connect! [["127.0.0.1" 9300]] {"cluster.name" clustername})))

  (search [es index mapping-types {:keys [size query fields flt] :as options}]
    (esnd/search index mapping-types :size size :query query :fields fields :filter flt))

  (put [es index mapping-type id document]     
    (esnd/put index mapping-type id document))

  (delete-by-query-across-all-types [es index-name query] 
    (esnd/delete-by-query-across-all-types index-name query))  

  (delete [es index-name mapping-type id]
    (esnd/delete index-name mapping-type id))

  (exists? [es index-name]
    (esni/exists? index-name))

  (update-mapping [es index-name-or-names type-name {:keys [mapping]}] 
    (esni/update-mapping index-name-or-names type-name :mapping {type-name mapping}))

  (create [es index-name options]
    (esni/create index-name :mappings options)))

;; (esr/connect! "http://127.0.0.1:9200")
(let [parent {:index "not_analyzed", :type "string", :store "yes"}
      token {:index "not_analyzed",
             :type "string",
             :store true,
             :null_value token-document-null-value}]
  (def mapping-types
    {"doc" {:_all {:enabled false},
            :_source {:enabled true},
            :properties
            {:parent parent
             :tags {:index "not_analyzed",
                    :type "string",
                    :index_options "docs",
                    :store true,
                    :omit_norms true},
             :lastmodified {:type "date", :store "yes"},
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
  (str (tcoerce/from-long (* 1000 (long mtime)))))

(defn lastmodified->mtime
  "convert elasticsearch date string to unix timestamp"
  [lastmodified]
  (quot (tcoerce/to-long (tcoerce/from-string lastmodified)) 1000))


(defn strip-mime-type-parameters
  "strip mime type parameters from mime type string"
  [s]
  (string/trim (first (string/split s #";" 2))))


(defn ensure-index-and-mappings
  [index-name]
  (if (exists? es index-name)
    (doseq [[doctype mapping] mapping-types]
      ;; (println "update-mapping" doctype mapping)
      (update-mapping es index-name doctype {:mapping mapping}))
    (create es index-name mapping-types)))

(defn es-listdir
  [index-name parent]
  (search es index-name
          ["dir" "doc"]
          {:size 1000000
           :query {:match_all {}}
           :fields ["parent" "lastmodified"]
           :flt {:term {:parent parent}}}))

(let [estype->type {"dir" "directory"
                    "doc" "file"}]
  (defn enrich-es-entries
    [parent entries]
    (let [convert-entry (fn [{:keys [_type _id fields _fields]}]
                          {:id _id
                           :type (estype->type _type)
                           :mtime (lastmodified->mtime (:lastmodified (or fields _fields)))})]
      (loop [entries entries]
        (if (map? entries)
          (recur (:hits entries))
          (map convert-entry entries))))))


(defn es-recursive-delete
  [index-name parent]
  (let [with-slash (misc/ensure-endswith-slash parent)]
    (delete-by-query-across-all-types 
      es
      index-name
     {:prefix {:_id with-slash}})
    (delete es index-name "dir" parent)))



(defn make-id
  [& args]
  (string/replace (string/join "/" args) #"/+" "/"))

(defn enrich-mq-entries
  [directory entries]
  (map
   (fn [entry]
     (assoc entry
       :id (make-id "" directory (:relpath entry))
       :type (if (:error entry)
               "error"
               (get-in entry [:stat :type]))
       :mtime (get-in entry [:stat :mtime])))
   entries))

(defn make-id-map
  [entries]
  (apply hash-map (mapcat (juxt :id identity) entries)))

(defn find-missing-entries
  [existing-map entries]
  (remove #(contains? existing-map (:id %)) entries))

(defn compare-directories
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
     :update-files nil}))


(defn apply-diff-to-elasticsearch
  [{:keys [delete-directories delete-files create-directories]} es-index parent-id]
  (doseq [e delete-directories]
    (es-recursive-delete es-index (:id e)))
  (doseq [e delete-files]
    (delete es es-index "doc" (:id e)))
  (doseq [e create-directories]
    (put es es-index "dir"
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
  [fs es-index {:keys [directory entry] :as body} {publish :publish}]
  (let [parent-id (make-id "" directory)
        relpath (:relpath entry)
        id (make-id "" directory relpath)
        title (or (get-in body [:extract :tika-content :dc:title])
                  relpath)
        simple-perms (simplify-permissions-for-es (:permissions entry))]


    (put es es-index "doc"
             id
             {:parent parent-id
              :content (get-in body [:extract :tika-content :text])
              :extension (path/get-extension-from-basename relpath)
              :content_type (strip-mime-type-parameters
                             (or
                              (first (get-in body [:extract :tika-content :content-type]))
                              ""))

              :title title
              :tags (get-tags-from-path directory)
              :allow_token_document (simple-perms :allow)
              :deny_token_document (simple-perms :deny)
              :lastmodified (mtime->lastmodified (get-in entry [:stat :mtime]))})))



(defn simple-update_directory
  [fs es-index {:keys [directory entries] :as body} {publish :publish}]
  ;; (logging/info "simple-update" directory)
  (let [parent-id (make-id "" directory)
        es-entries (enrich-es-entries parent-id (es-listdir es-index parent-id))
        mq-entries (enrich-mq-entries directory entries)
        diff (compare-directories mq-entries es-entries)]
    (apply-diff-to-elasticsearch diff es-index parent-id)

    (doseq [e (:create-files diff)]
      (publish "extract_content"
               {:directory directory
                :entry (select-keys e [:relpath :permissions :stat])}))))


(def command->msg-handler
  {"update_directory" simple-update_directory
   "import_file"      simple-import_file})

(defn build-handle-connection
  [fsmap num-workers rmq-prefix]
  (fn [conn]
    (logging/info "initializing rabbitmq connection with" num-workers "workers")
    (doseq [_ (range num-workers)]
      (mqhelper/channel-loop
       conn
       (fn [ch]
         (doseq [fs (keys fsmap)
                 [command handle-msg] (seq command->msg-handler)]
           (let [qname (mqhelper/initialize-rabbitmq-structures ch command rmq-prefix fs)]
             ;; (lb/qos ch 1)
             (lcons/subscribe ch qname
                              (mqhelper/make-handler (partial handle-msg fs (get-in fsmap [fs :index])))))))))))

(defn make-standard-fsmap
  [filesystems]
  (into
   {}
   (for [fs filesystems]
     [fs {:index fs :prefix "" :filesystem fs}])))


(defn indexes-from-fsmap
  [fsmap]
  (distinct (map :index (vals fsmap))))


(defn ensure-all-indexes-and-mappings
  [fsmap]
  (doseq [idx (indexes-from-fsmap fsmap)]
    (ensure-index-and-mappings idx)))

(defn make-elastisch-client
  [{:keys [es-connection-type es-clustername es-url] :as es-config}]
  (cond (= es-connection-type "native")
          (->NativeElastisch es-clustername false)
        (= es-connection-type "local")
          (->NativeElastisch es-clustername true)
        (= es-connection-type "rest")
          (->RestElastisch es-url)))

;; alter variable for es client with client instance
(defn initialize-elasticsearch
  [es-config fsmap]
  (alter-var-root #'es (constantly (make-elastisch-client es-config)))
  (connect es)
  (ensure-all-indexes-and-mappings fsmap))


(defrecord ESConnectService [rmq-settings rmq-prefix num-workers fsmap es-config thread-pool]
  worker/Service
  (start [this]
    (try-try-again {:tries :unlimited
                    :error-hook (fn [err]
                                  (trace/print-stack-trace err)
                                  (logging/error "error while initializing elasticsearch connection and indexes" es-config err))}
                   #(initialize-elasticsearch es-config fsmap))

    (mqhelper/connect-loop-with-thread-pool
     rmq-settings
     (build-handle-connection fsmap num-workers rmq-prefix)
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
            filesystems (sys/get-filesystems-for-section system section)
            fsmap (make-standard-fsmap filesystems)
            es-config {:es-connection-type (-> system :config :es-connection-type)
                       :es-clustername (-> system :config :es-clustername)
                       :es-url (-> system :config :es-url)}]
        (->ESConnectService rmq-settings
                            rmq-prefix
                            num-workers
                            fsmap
                            es-config
                            (:thread-pool system))))))
