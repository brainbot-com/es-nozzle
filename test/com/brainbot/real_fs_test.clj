(ns com.brainbot.real-fs-test
  (:import [java.nio.file.attribute PosixFilePermission])

  (:require [clojure.test :refer :all]
            [com.brainbot.real-fs :as real-fs]))


(deftest test-acl-from-posix-perm
  (testing "owner-write"
    (is (= (#'real-fs/acl-from-posix-perm "owner" "group" "others" #{PosixFilePermission/OWNER_WRITE})
           '())))
  (testing "group-read"
    (is (= (#'real-fs/acl-from-posix-perm "owner" "group" "others" #{PosixFilePermission/GROUP_READ})
           '({:allow false, :sid "owner"} {:allow true, :sid "group"})))))

