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

(deftest test-is-apple-garbage?
  (is (true? (is-apple-garbage? {:relpath ".AppleDouble"})))
  (is (true? (is-apple-garbage? {:relpath "__MACOSX"})))
  (is (true? (is-apple-garbage? {:relpath ".DS_Store"})))
  (is (true? (is-apple-garbage? {:relpath "._foobar"})))
  (is (false? (is-apple-garbage? {:relpath "foo-bar-baz"})))
  (is (false? (is-apple-garbage? {:relpath "a"}))))

