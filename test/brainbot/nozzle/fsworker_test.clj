(ns brainbot.nozzle.fsworker-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.fsworker :refer :all]))

(deftest test-entry-is-type-ok?
  (is (false? (entry-is-type-ok? :file {:error "err" :stat {:type :file}})))
  (is (false? (entry-is-type-ok? :directory {:stat {:type :file}})))
  (is (true? (entry-is-type-ok? :file {:stat {:type :file}}))))
