(ns brainbot.nozzle.core-test
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.misc :refer :all]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.fsworker :refer :all]))

(deftest test-rmq-settings-from-config
  (testing "no amqp-url"
    (is (= (inihelper/rmq-settings-from-config {inihelper/main-section-name {}})
           {:api-endpoint "http://localhost:15672" :username "guest", :password "guest", :vhost "/", :host "localhost", :port 5672})))
  (testing "with amqp-url"
    (is (= (inihelper/rmq-settings-from-config {inihelper/main-section-name {"amqp-url" "amqp://foo.com/host"}})
           {:api-endpoint "http://foo.com:15672" :host "foo.com", :port 5672, :vhost "host", :username "guest", :password "guest"}))))
