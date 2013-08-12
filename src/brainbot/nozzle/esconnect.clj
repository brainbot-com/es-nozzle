(ns brainbot.nozzle.esconnect
  (:require [clojure.tools.logging :as logging])
  (:require [brainbot.nozzle.misc :as misc])
  (:require [clojurewerkz.elastisch.rest :as esr]
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
    #_(doseq [fs filesystems]
      (future (manage-filesystem "nextbot" fs)))))
