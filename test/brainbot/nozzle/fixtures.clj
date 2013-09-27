(ns brainbot.nozzle.fixtures
  (:require [clojurewerkz.elastisch.native :as esn]
            [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojure.java.io :as io])
  (:import  [java.nio.file Files FileSystems]
            [org.elasticsearch.common.settings ImmutableSettings]
            [org.elasticsearch.node NodeBuilder]))

(defn- create-all []
  "Creates documents for several permutations of access- and deny-tokens"
  (let [deny_conditions [["USER:myself"] 
                         ["USER:someoneelse" "USER:myself"] 
                         ["GROUP:everyone"]]
        dont_deny_conditions [[nil] 
                              ["USER:someoneelse"]]
        allow_conditions [["USER:myself"] 
                          ["USER:myself" "GROUP:everyone"] 
                          ["USER:someoneelse" "GROUP:everyone"] 
                          ["GROUP:everyone"]]
        dont_allow_conditions [[nil]
                               ["USER:someoneelse"]]]
    
    (defn- create-documents [allows denies tag]
      (doseq [allow allows
              deny denies]
        (esd/create "rightstest" "rightsdoc" {:allow_token_document allow :deny_token_document deny :tag tag})))

    (create-documents allow_conditions dont_deny_conditions "findme") 
    (create-documents allow_conditions deny_conditions "findmenot")
    (create-documents dont_allow_conditions dont_deny_conditions "findmenot")
    (create-documents dont_allow_conditions deny_conditions "findmenot")))

(defn- delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
  Raise an exception if any deletion fails unless silently is true.
  Taken from: https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/java_utils.clj#L185:L193"
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (io/delete-file f silently)))


(defn- connect []
  "Connects a local client with a temporary data directory"
  (let [localpath (.. (FileSystems/getDefault) (getPath (System/getProperty "java.io.tmpdir") (into-array String [])))
        tempdir (Files/createTempDirectory localpath "testdata" (into-array java.nio.file.attribute.FileAttribute [])) 
        tempdirname (.. tempdir (toFile) (getAbsolutePath))
        settings (.. (ImmutableSettings/settingsBuilder) (put "path.data" tempdirname) (build)) 
        clientnode (.. (NodeBuilder/nodeBuilder) (settings settings) (local true) (clusterName "nozzletestfiltercluster") (node))]
    (.. (Runtime/getRuntime) (addShutdownHook (Thread. #(delete-file-recursively tempdirname))))
    (esn/connect-to-local-node! clientnode)))

(defn- prepare []
  "Creates the mapping for the testindex"
  (let [token-document-null-value "NOBODY"
        token {:index "not_analyzed" 
               :type "string" 
               :store "yes" 
               :null_value token-document-null-value}
        tag {:index "not_analyzed" 
             :type "string" 
             :store "yes"}
        mapping {"rightsdoc" 
                 {:properties 
                  {:tag tag 
                   :allow_token_document token
                   :deny_token_document token}}}]
    (esi/create "rightstest" :mappings mapping)))

(defn es-filter-test-setup [f]
  "Fixture for es-filter-test. Wraps the test-function f in setup and teardown."
  ; connect client
  (connect)
  ; prepare index
  (prepare)
  ; index data
  (create-all)
  ; refresh, i.e. make searchable
  (esi/refresh "rightstest")
  (f)
  ; no teardown, since temporary data is automatically deleted on exit
  )

