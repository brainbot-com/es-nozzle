(ns brainbot.nozzle.inihelper-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.inihelper :refer :all]
            [brainbot.nozzle.fsfilter :as fsfilter]))

(deftest test-loading-dotfiles
  (let [relpath-filter (dynaload-section {"dotfile" {"type" "dotfile"}} "dotfile")]
    (is (true? (fsfilter/matches-relpath? relpath-filter ".foo")))
    (is (false? (fsfilter/matches-relpath? relpath-filter "foo")))))
