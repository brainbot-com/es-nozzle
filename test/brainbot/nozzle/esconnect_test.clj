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
    (is (=submap (compare-directories dir-listing-with-error [])
                 {:create-directories []}))
    (is (=submap (compare-directories dir-listing-with-error dir-listing1)
                 {:delete-directories []}))))

(deftest test-date-converters
  (testing "mtime->lastmodified"
    (is (= "1970-01-01T00:00:00.000Z" (mtime->lastmodified 0)))
    (is (= "2013-08-20T08:38:20.000Z" (mtime->lastmodified 1376987900))))
  (testing "lastmodified->mtime"
    (is (= 0 (lastmodified->mtime "1970-01-01T00:00:00.000Z")))
    (is (= 1376987900 (lastmodified->mtime "2013-08-20T08:38:20.000Z")))))


(deftest test-strip-mime-type-parameters
  (is (= (strip-mime-type-parameters "text/plain") "text/plain"))
  (is (= (strip-mime-type-parameters "text/plain ;encoding=utf8") "text/plain")))

(deftest test-make-id
  (is (= (make-id "foo" "bar" "//baz") "foo/bar/baz")))

(deftest test-permset
  (is (= (permset ["foo" "bar" "baz"]) #{"foo" "bar" "baz"}))
  (is (= (permset "foo") #{"foo"})))

(deftest test-get-tags-from-path
  (is (= (get-tags-from-path "/foo/bar/foo/bar//baz")
         ["bar" "baz" "foo"])))
