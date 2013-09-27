(ns brainbot.nozzle.misc-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.misc :refer :all]))

(deftest test-trimmed-lines-from-string
  (testing "trimmed lines from string should work with nil parameter"
    (is (nil? (trimmed-lines-from-string nil))))
  (testing "trimmed lines should skip empty lines"
    (is (= (trimmed-lines-from-string "  foo  \nbar\n\n  baz   \n")
           ["foo" "bar" "baz"]))))

(deftest test-remap
  (testing "basic remap"
    (is (remap inc {}) {})
    (is (remap inc {:a 1 :b 2}) {:a 2 :b 3})))

(deftest test-endswith-slash
  (is (= (ensure-endswith-slash "") "/"))
  (is (= (ensure-endswith-slash "/") "/"))
  (is (= (ensure-endswith-slash "foo/") "foo/"))
  (is (= (ensure-endswith-slash "foo/bar") "foo/bar/"))
  (is (= (ensure-endswith-slash "foo/") "foo/")))
