(ns brainbot.nozzle.real-fs
  (:require
   [clojure.string :as string])
  (:require [nio2.dir-seq])
  (:import [java.io File IOException FileNotFoundException]
           [java.nio.file Files Path LinkOption Paths AccessDeniedException]

           [java.nio.file.attribute UserPrincipal GroupPrincipal AclEntryType AclEntryPermission AclFileAttributeView PosixFilePermissions PosixFilePermission BasicFileAttributes PosixFileAttributes])
  (:require [brainbot.nozzle.extract :refer [convert]])

  (:require [brainbot.nozzle.vfs :as vfs]))


(def ^:private is-windows
  (= "\\" File/separator))

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



(defn- raw-windows-acls
  [path]
  (-> path
      get-path
      (Files/getFileAttributeView AclFileAttributeView (into-array LinkOption []))
      .getAcl
      seq))

(defn- convert-raw-windows-sid
  [sid]
  (let [name (.getName sid)]
    (cond
      (instance? UserPrincipal sid)
        (str "USER:" name)
      (instance? GroupPrincipal sid)
        (str "GROUP:" name))))


(defn- convert-raw-windows-ace
  [ace]
  (if (contains? (.permissions ace) AclEntryPermission/READ_DATA)
    {:allow (= AclEntryType/ALLOW (.type ace))
     :sid (convert-raw-windows-sid (.principal ace))}))

(defn- windows-acl-from-path
  [path]
  (remove nil? (map convert-raw-windows-ace (raw-windows-acls path))))

(defn- posix-acl-from-path
  [path]
  (acl-from-attribute (read-attributes path)))

(defn- collapse-consecutive-slash
  [s]
  (string/replace s #"/+" "/"))

(defn- trim-slash
  [s]
  (string/replace s #"^/+|/+$" ""))


(defn- normalize-path
  [path]
  (let [tmp (-> path
              collapse-consecutive-slash
              trim-slash)]
    (if (= "" tmp)
      "/"
      tmp)))


(defrecord RealFilesystem [root]
  vfs/Filesystem
  (extract-content [fs entry]
    (let [fp (string/join "/" [(:root fs) entry])]
      {:tika-content (convert fp)}))

  (access-denied-exception? [fs err]
    (instance? AccessDeniedException err))

  (get-permissions [fs entry]
    (let [fp (string/join "/" [(:root fs) entry])]
      (if is-windows
        (windows-acl-from-path fp)
        (posix-acl-from-path fp))))

  (stat [fs entry]
    (let [fp (string/join "/" [(:root fs) entry])
          attr (read-attributes fp)
          ct (fn [t] (/ (.toMillis t) 1000))]
      {:type (type-from-attribute attr)
       :size (.size attr)
       :mtime (ct (.lastModifiedTime attr))}))

  (join [fs parts]
    (normalize-path (string/join "/" parts)))

  (listdir [fs dir]
    (let [fp (string/join "/" [(:root fs) dir])
          up (get-path fp)]
      (map #(-> % .getFileName str)
           (nio2.dir-seq/dir-seq up)))))


(defn filesystem-from-inisection
  [section]
  (let [path (section "path")]
    (when-not path
      (throw (Exception. "no path specified in section")))
    (RealFilesystem. path)))
