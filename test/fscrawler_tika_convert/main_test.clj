(ns fscrawler-tika-convert.main-test
  (:require [clojure.test :refer :all]
            [fscrawler-tika-convert.main :refer :all]))


(defn mock-die
  [msg & {:keys [exit-code] :or {exit-code 1}}]
  (throw (Exception. (str exit-code " " msg))))


(deftest test-trimmed-lines-from-string
  (testing "trimmed lines from string should work with nil parameter"
    (is (nil? (trimmed-lines-from-string nil))))
  (testing "trimmed lines should skip empty lines"
    (is (= (trimmed-lines-from-string "  foo  \nbar\n\n  baz   \n")
           ["foo" "bar" "baz"]))))


(deftest test-parse-command-line-options
  (testing "calling program with all arguments supplied"
    (is (= (parse-command-line-options ["--iniconfig" "/foo/config.ini" "--inisection" "baz"])
           {:iniconfig "/foo/config.ini"
            :inisection "baz"})))

  (with-redefs [die mock-die]
    (testing "--help should show help message"
      (is (thrown-with-msg?
           Exception
           #"0 "
           (parse-command-line-options ["--help"]))))

    (testing "program should die when called without --inisection/--iniconfig"
      (is (thrown-with-msg?
           Exception
           #"1 --iniconfig option missing"
           (parse-command-line-options ["--inisection" "bla"])))

      (is (thrown-with-msg?
           Exception
           #"1 --inisection option missing"
           (parse-command-line-options ["--iniconfig" "config.ini"]))))))


(deftest test-die-on-exit-or-error
  (with-redefs [die mock-die]
    (testing "die-on-exit-or-error should handle nil error"
      (let [a-future (future (/ 1 0))
            error (try
                    @a-future
                    (catch Exception err
                      err))]
        (is (thrown-with-msg?
           Exception
           #"1 thread died unexpectedly"
           (die-on-exit-or-error a-future error)))))))
