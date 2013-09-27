(ns brainbot.nozzle.fsfilter-test
  (:require [clojure.test :refer :all])
  (:require [brainbot.nozzle.fsfilter :refer :all]))


(deftest test-normalize-extension
  (let [has-ext? (comp
                  (make-has-extension? ["FOO", "bar", ".baz"])
                  (fn [s] {:relpath s :stat {:type :file}}))]
    (is (false? (has-ext? "example.c")))
    (is (false? (has-ext? ".baz")))
    (is (false? (has-ext? "foo.baz.c")))
    (is (true? (has-ext? "example.BAR")))
    (is (true? (has-ext? "example.bar")))
    (is (true? (has-ext? "example.baz")))
    (is (true? (has-ext? "example.foo")))))

(deftest test-normalize-extension-type
  (let [has-ext? (make-has-extension? [".c"])]
    (is (true? (has-ext? {:relpath "bla.c" :stat {:type :file}})))
    (is (false? (has-ext? {:relpath "bla.c" :stat {:type :directory}})))))
