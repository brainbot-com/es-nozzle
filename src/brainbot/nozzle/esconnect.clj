(ns brainbot.nozzle.esconnect
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
              :filter {:term {:parent parent}}))


(defn es-recursive-delete
  [index-name parent]
  (let [with-slash (misc/ensure-endswith-slash parent)]
    (esd/delete-by-query-across-all-types
     index-name
     {:prefix {:_id with-slash}})
    (esd/delete index-name "dir" parent)))




(defn simple-update_directory
  [fs es-index {:keys [directory entries] :as body} {publish :publish}]
  (println "update-directory" body)

  (doseq [e (filter #(= (get-in % [:stat :type]) "directory") entries)]
    (let [id (string/join "/" [directory (:relpath e)])]
      (println "put" e)
      (esd/put es-index "dir"
               id
               {:mtime (get-in e [:stat :mtime])
                :parent directory}))))



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
