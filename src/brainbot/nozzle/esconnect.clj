(ns brainbot.nozzle.esconnect
  (:require [clojure.pprint :refer [pprint]])
  (:require [clojure.string :as string])
  (:require [clojure.tools.logging :as logging])
  (:require [langohr.core :as rmq]
            [langohr.consumers :as lcons]
            [langohr.basic :as lb])
  (:require [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.mqhelper :as mqhelper])
  (:require [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]))

;; (esr/connect! "http://127.0.0.1:9200")

(def mapping-types
  {"doc" {:_all {:enabled false},
          :properties
          {:name {:index "not_analyzed", :type "string", :store "yes"},
           :parent {:index "not_analyzed", :type "string", :store "yes"},
           :tags
           {:index "not_analyzed",
            :type "string",
            :index_options "docs",
            :store true,
            :omit_norms true},
           :lastmodified {:type "integer", :store "yes"},
           :content {:type "string", :store "yes"},
           :deny_token_document
           {:index "not_analyzed",
            :type "string",
            :store true,
            :null_value "UNAUTHENTICATED"},
           :allow_token_document
           {:index "not_analyzed",
            :type "string",
            :store true,
            :null_value "NOBODY"}},
          :_source {:enabled false}},
   "dir" {:_all {:enabled false},
          :properties
          {:lastmodified {:type "integer", :store "yes"},
           :name {:index "not_analyzed", :type "string", :store "yes"},
           :parent {:index "not_analyzed", :type "string", :store "yes"}},
          :_source {:enabled false}}})


(defn ensure-index-and-mappings
  [index-name]
  (if (esi/exists? index-name)
    (doseq [[doctype mapping] mapping-types]
      (println "update-mapping" doctype mapping)
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
                           :mtime (:lastmodified fields)})]
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

        es-entries-by-type
          (group-by :type (find-missing-entries (mq-entries-by-type "error") es-entries))
        es-directory-map (make-id-map (es-entries-by-type "directory"))
        es-file-map (make-id-map (es-entries-by-type "file"))]
    (let [retval {:delete-directories (find-missing-entries mq-directory-map (es-entries-by-type "directory"))
                  :create-directories (find-missing-entries es-directory-map (mq-entries-by-type "directory"))
                  :delete-files nil
                  :create-files nil
                  :update-files nil}]

      (pprint {:compare-directories
               {:in {:mq-directory-map mq-directory-map
                     :es-directory-map es-directory-map}
                :out retval}})
      (println)
      retval)))


(defn simple-update_directory
  [fs es-index {:keys [directory entries] :as body} {publish :publish}]
  (let [parent-id (make-id "" directory)
        es-entries (enrich-es-entries parent-id (es-listdir es-index parent-id))
        mq-entries (enrich-mq-entries directory entries)]

    (compare-directories mq-entries es-entries)))




    ;; (doseq [e (entries-by-type "directory")]
    ;;   (println "put" e)
    ;;   (esd/put es-index "dir"
    ;;            (:id e)
    ;;            {:lastmodified (:mtime e)
    ;;             :parent parent-id}))))



(defn build-handle-connection
  [es-index filesystems]
  (fn [conn]
    (logging/info "initializing rabbitmq connection")
    (doseq [fs filesystems]
      (mqhelper/channel-loop
       conn
       (fn [ch]
         (let [qname (misc/initialize-rabbitmq-structures ch "update_directory" "nextbot" fs)]
           (logging/info "starting consumer for" qname)
           (lb/qos ch 1)
           (lcons/subscribe ch qname
                            (mqhelper/make-handler (partial simple-update_directory fs es-index)))))))))


(defn esconnect-run-section
  [iniconfig section]
  (let [rmq-settings (misc/rmq-settings-from-config iniconfig)
        filesystems (misc/get-filesystems-from-iniconfig iniconfig section)
        es-index (get-in iniconfig [misc/main-section-name "es-index"])
        es-url (or (get-in iniconfig [misc/main-section-name "es-url"]) "http://localhost:9200")]

    (when (empty? filesystems)
      (misc/die (str "no filesystems defined in section " section)))

    (when-not es-index
      (misc/die "es-index setting missing"))

     (logging/info "connecting to elasticsearch" es-url)
    (esr/connect! es-url)
    (ensure-index-and-mappings es-index)
    (mqhelper/connect-loop
     #(rmq/connect rmq-settings)
     (build-handle-connection es-index filesystems))))
