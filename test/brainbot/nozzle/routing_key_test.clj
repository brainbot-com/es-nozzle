(ns brainbot.nozzle.routing-key-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.routing-key :refer :all]))


(deftest test-map-from-routing-key
  (testing "map-from-routing-key"
    (is (= (map-from-routing-key-string "foo.bar.baz")
           {:id "foo" :filesystem "bar" :command "baz"}))))

(deftest test-routing-key-string
  (testing "routing-key-string"
    (is (= (routing-key-string {:id "foo" :filesystem "bar" :command "baz"})
           "foo.bar.baz"))
    (is (= (routing-key-string "foo" "bar" "baz")
           "foo.bar.baz"))))

(deftest test-routing-key-string-with-command
  (testing "routing-key-string-with-command"
    (is (= (routing-key-string-with-command "foo.bar.baz" "bob")
           "foo.bar.bob"))))
