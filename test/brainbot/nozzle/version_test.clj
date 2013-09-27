(ns brainbot.nozzle.version-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.version :refer :all]))

(deftest test-version
  (is (= (nozzle-version)
         (enhanced-version "com.brainbot" "es-nozzle")
         (enhanced-version "com.brainbot/es-nozzle")))
  (is (not (empty? (enhanced-version "clj-time"))))
  (is (not (empty? (nozzle-version)))))
