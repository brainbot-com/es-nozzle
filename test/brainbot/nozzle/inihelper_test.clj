(ns brainbot.nozzle.inihelper-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.sys :as sys]
            [brainbot.nozzle.inihelper :refer :all]
            [brainbot.nozzle.fsfilter :as fsfilter]))

(deftest test-loading-dotfiles
  (let [relpath-filter (dynaload-section
                        (sys/make-system {"dotfile" {"type" "dotfile"}} [])
                        "dotfile")]
    (is (true? ((fsfilter/make-match-entry? relpath-filter) {:relpath ".foo"})))
    (is (false?((fsfilter/make-match-entry? relpath-filter) {:relpath "foo"})))))
