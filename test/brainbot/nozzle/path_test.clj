(ns brainbot.nozzle.path-test
  (:require [clojure.test :refer :all])
  (:require [brainbot.nozzle.path :refer :all]))


(deftest test-get-extension-from-basename
  (testing "basic operations"
    (is (= (get-extension-from-basename "foo")
           ""))
    (is (= (get-extension-from-basename "foo.BaR")
           ".bar"))
    (is (= (get-extension-from-basename "foo.bar.baz")
           ".baz"))
    (is (= (get-extension-from-basename ".zshrc")
           ""))
    (is (= (get-extension-from-basename "foo.")
           "."))))
