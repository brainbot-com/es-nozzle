(ns brainbot.nozzle.core-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.extract :refer :all]
            [brainbot.nozzle.misc :refer :all]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.fsworker :refer :all]))


(deftest test-wash
  (testing "wash should replace 0xfffd character with whitespace"
    (is (= (wash (str "hello" (char 0xfffd) "world"))
           "hello world")))
  (testing "wash should remove 0xfffd character at start/end of string"
    (is (= (wash (str (char 0xfffd) "hello" (char 0xfffd) "world" (char 0xfffd)))
           "hello world"))))

(deftest test-rmq-settings-from-config
  (testing "no amqp-url"
    (is (= (inihelper/rmq-settings-from-config {inihelper/main-section-name {}})
           {:username "guest", :password "guest", :vhost "/", :host "localhost", :port 5672})))
  (testing "with amqp-url"
    (is (= (inihelper/rmq-settings-from-config {inihelper/main-section-name {"amqp-url" "amqp://foo.com/host"}})
           {:host "foo.com", :port 5672, :vhost "host", :username "guest", :password "guest"}))))
