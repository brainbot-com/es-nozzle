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
  (if (esi/exists? index-name)
    (doseq [[doctype mapping] mapping-types]
      ;; (println "update-mapping" doctype mapping)
      (esi/update-mapping index-name doctype :mapping {:mapping mapping}))
    (esi/create index-name :mappings mapping-types)))


(defn es-listdir
  [index-name parent]
  (esd/search index-name
              ["dir" "doc"]
              :size 1000000
              :query {:match_all {}}
              :fields ["parent" "lastmodified"]
              :filter {:term {:parent parent}}))

(let [estype->type {"dir" "directory"
                    "doc" "file"}]
  (defn enrich-es-entries
    [parent entries]
    (let [convert-entry (fn [{:keys [_type _id fields]}]
                          {:id _id
                           :type (estype->type _type)
                           :mtime (lastmodified->mtime (:lastmodified fields))})]
      (loop [entries entries]
        (if (map? entries)
          (recur (:hits entries))
          (map convert-entry entries))))))


(defn es-recursive-delete
  [index-name parent]
  (let [with-slash (misc/ensure-endswith-slash parent)]
    (esd/delete-by-query-across-all-types
     index-name
     {:prefix {:_id with-slash}})
    (esd/delete index-name "dir" parent)))



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
  [fs es-index {:keys [directory entry] :as body} {publish :publish}]
  (let [parent-id (make-id "" directory)
        relpath (:relpath entry)
        id (make-id "" directory relpath)
        title (or (get-in body [:extract :tika-content :dc:title])
                  relpath)
        simple-perms (simplify-permissions-for-es (:permissions entry))]


    (esd/put es-index "doc"
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
  [fsmap num-workers]
  (fn [conn]
    (logging/info "initializing rabbitmq connection with" num-workers "workers")
    (doseq [_ (range num-workers)]
      (mqhelper/channel-loop
       conn
       (fn [ch]
         (doseq [fs (keys fsmap)
                 [command handle-msg] (seq command->msg-handler)]
           (let [qname (misc/initialize-rabbitmq-structures ch command "nextbot" fs)]
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

(defn initialize-elasticsearch
  [es-url fsmap]
  (esr/connect! es-url)
  (ensure-all-indexes-and-mappings fsmap))


(defrecord ESConnectService [rmq-settings num-workers fsmap es-url]
  worker/Service
  (start [this]
    (try-try-again {:tries :unlimited
                    :error-hook (fn [err]
                                  (logging/error "error while initializing elasticsearch connection and indexes" es-url err))}
                   #(initialize-elasticsearch es-url fsmap))

    (mqhelper/connect-loop-with-thread-pool
     rmq-settings
     (build-handle-connection fsmap num-workers)
     :thread-pool-size num-workers)))


(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section]
      (let [rmq-settings (misc/rmq-settings-from-config iniconfig)
            num-workers (Integer. (get-in iniconfig [section "num-workers"] "10"))
            filesystems (misc/get-filesystems-from-iniconfig iniconfig section)
            fsmap (make-standard-fsmap filesystems)
            es-url (or (get-in iniconfig [misc/main-section-name "es-url"]) "http://localhost:9200")]

        (when (empty? filesystems)
          (misc/die (str "no filesystems defined in section " section)))
        (->ESConnectService rmq-settings num-workers fsmap es-url)))))
