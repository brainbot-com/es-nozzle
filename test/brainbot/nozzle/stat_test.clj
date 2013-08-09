(ns brainbot.nozzle.stat-test
  (:import [java.nio.file.attribute PosixFilePermission])
  (:require [clojure.test :refer :all]
            [brainbot.nozzle.stat :refer :all]))

(defn octal-mode-from-type-and-permissions
  [type permissions]
  (format "%o" (st-mode-from-type-and-permissions type permissions)))


(def mode-755
  [PosixFilePermission/OWNER_READ
   PosixFilePermission/OWNER_WRITE
   PosixFilePermission/OWNER_EXECUTE
   PosixFilePermission/GROUP_READ
   PosixFilePermission/GROUP_EXECUTE
   PosixFilePermission/OTHERS_READ
   PosixFilePermission/OTHERS_EXECUTE])


(deftest test-st-mode
  (testing "file permissions"
    (is (= "100755" (octal-mode-from-type-and-permissions :file mode-755))))

  (testing "empty permissions"
    (is (= "100000" (octal-mode-from-type-and-permissions :file [])))))
