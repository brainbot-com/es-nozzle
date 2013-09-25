(ns brainbot.nozzle.tika-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.tika :refer :all]))

(def wash #'brainbot.nozzle.tika/wash)

(deftest test-wash
  (testing "wash should replace 0xfffd character with whitespace"
    (is (= (wash (str "hello" (char 0xfffd) "world"))
           "hello world")))
  (testing "wash should remove 0xfffd character at start/end of string"
    (is (= (wash (str (char 0xfffd) "hello" (char 0xfffd) "world" (char 0xfffd)))
           "hello world"))))
