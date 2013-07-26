(ns com.brainbot.real-fs
  (:require
   [clojure.string :as string])
  (:import [java.io File IOException FileNotFoundException]
           [java.nio.file Files Path LinkOption Paths]
           [java.nio.file.attribute AclFileAttributeView PosixFilePermissions PosixFilePermission BasicFileAttributes PosixFileAttributes])

  (:require [com.brainbot.vfs :as vfs]))


(def ^:private no-follow-links
  (into-array [LinkOption/NOFOLLOW_LINKS]))

(defn- get-path
  [path & args]
  (Paths/get path (into-array String args)))


(defn- read-attributes
  [path]
  (Files/readAttributes (get-path path) PosixFileAttributes no-follow-links))


(defn- type-from-attribute
  [attr]
  (cond
    (.isDirectory attr)
      :directory
    (.isRegularFile attr)
      :file
    (.isSymbolicLink attr)
      :symbolic-link
    :else
      :other))


(defn- acl-from-posix-perm
  [owner-name group-name others-name perm]
  (reverse
   (drop-while  ;; drop deny rules from the end of the acl
    (complement :allow)
    (map (fn [name rperm]
           {:allow (contains? perm rperm)
            :sid name})
         [others-name group-name owner-name]
         [PosixFilePermission/OTHERS_READ
          PosixFilePermission/GROUP_READ
          PosixFilePermission/OWNER_READ]))))


(defn- acl-from-attribute
  [attr]
  (let [perm (.permissions attr)
        owner-name (str "USER:" (.getName (.owner attr)))
        group-name (str "GROUP:" (.getName (.group attr)))]
    (acl-from-posix-perm owner-name group-name "GROUP:AUTHENTICATED_USERS" perm)))


(defrecord RealFilesystem [root]
  vfs/Filesystem
  (get-permissions [fs entry]
    (let [fp (string/join "/" [(:root fs) entry])
          attr (read-attributes fp)]
      (acl-from-attribute attr)))

  (stat [fs entry]
    (let [fp (string/join "/" [(:root fs) entry])
          attr (read-attributes fp)
          ct (fn [t] (/ (.toMillis t) 1000))]
      {:type (type-from-attribute attr)
       :size (.size attr)
       :mtime (ct (.lastModifiedTime attr))}))

  (join [fs parts]
    (string/join "/" parts))

  (listdir [fs dir]
    (seq (.list (clojure.java.io/file (string/join "/" [(:root fs) dir]))))))
