(ns brainbot.nozzle.esconnect-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.esconnect :refer :all]))

(def dir-listing1
  [{:type "directory" :id "/a/b"}])

(def dir-listing-with-error
  [{:type "error" :id "/a/b"}])

(def dir-listing-empty
  [])

(defn =submap
  [map submap]
  (= (select-keys map (keys submap)) submap))

(deftest test-compare-directories
  (testing "compare-directories basic operations"
    (is (=submap (compare-directories dir-listing1 [])
                 {:create-directories dir-listing1}))
    (is (=submap (compare-directories [] dir-listing1)
                 {:delete-directories dir-listing1}))
    (is (=submap (compare-directories dir-listing-with-error dir-listing1)
                 {:delete-directories []}))))
