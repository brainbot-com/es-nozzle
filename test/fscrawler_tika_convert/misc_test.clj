(ns fscrawler-tika-convert.misc-test
  (:require [clojure.test :refer :all]
            [fscrawler-tika-convert.misc :refer :all]))

(deftest test-trimmed-lines-from-string
  (testing "trimmed lines from string should work with nil parameter"
    (is (nil? (trimmed-lines-from-string nil))))
  (testing "trimmed lines should skip empty lines"
    (is (= (trimmed-lines-from-string "  foo  \nbar\n\n  baz   \n")
           ["foo" "bar" "baz"]))))
