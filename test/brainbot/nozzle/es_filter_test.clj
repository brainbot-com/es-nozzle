(ns brainbot.nozzle.es-filter-test
  (:require [clojure.test :refer :all] 
            [clojurewerkz.elastisch.native.document :as esd]
            [brainbot.nozzle.fixtures :as cfg]))

; SETUP
(use-fixtures :once cfg/es-filter-test-setup)

; UTILS
(def user-tokens ["USER:myself" "GROUP:everyone"])

(defn- getcount [result]
  (get-in result [:hits :total]))

; this is how one would define filters
(defn filteredquery [tokens]
  (let [is-allowed? {:terms 
                     {:allow_token_document tokens 
                      :execution "or"}}
        not-denied? {:not 
                     {:filter 
                      {:terms 
                       {:deny_token_document tokens 
                        :execution "or"}}}}
        accessfilter {:and
                      {:filters [is-allowed? not-denied?]}}
        filteredquery {:filtered
                       {:query {:match_all {}}
                        :filter accessfilter}}]
    filteredquery))


;; TESTS:
(deftest test-allowed
         (let [allowcount (getcount (esd/search-all-indexes-and-types :query (filteredquery user-tokens)))
               findmecount (getcount (esd/search-all-indexes-and-types :query {:term {:tag "findme"}}))]
           ; make sure there is something indexed
           (is (> allowcount 0))
           (is (= allowcount findmecount))))

(deftest test-denied
         (let [totalcount (getcount (esd/search-all-indexes-and-types :query {:match_all {}}))
               findmenotcount (getcount (esd/search-all-indexes-and-types :query {:term {:tag "findmenot"}})) 
               allowcount (getcount (esd/search-all-indexes-and-types :query (filteredquery user-tokens)))]
           (is (= findmenotcount (- totalcount allowcount)))))
