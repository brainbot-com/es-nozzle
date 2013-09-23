(ns brainbot.nozzle.real-fs
  (:require
   [clojure.string :as string])
  (:require [nio2.dir-seq])
  (:import [java.io File IOException FileNotFoundException]
           [java.nio.file Files Path LinkOption Paths AccessDeniedException]

           [java.nio.file.attribute UserPrincipal GroupPrincipal AclEntryType AclEntryPermission AclFileAttributeView PosixFilePermissions PosixFilePermission BasicFileAttributes PosixFileAttributes])
  (:require brainbot.nozzle.dynaload)
  (:require [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.vfs :as vfs]
            [brainbot.nozzle.tika :as tika]
            [brainbot.nozzle.path :refer [normalize-path]]
            [brainbot.nozzle.inihelper :as inihelper]))


(def ^:private is-windows
  (= "\\" File/separator))

(def ^:private no-follow-links
  (into-array [LinkOption/NOFOLLOW_LINKS]))

(def ^:private empty-link-options
  (into-array java.nio.file.LinkOption []))

(defn- get-path
  [path & args]
  (Paths/get path (into-array String args)))

(let [klass (if is-windows BasicFileAttributes PosixFileAttributes)
      link-options (if is-windows empty-link-options no-follow-links)]
  (defn- read-attributes
    [path]
    (Files/readAttributes (get-path path) klass link-options)))

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

(defrecord RealFilesystem [root]
  vfs/Filesystem
  (extract-content [fs entry]
    (let [fp (string/join "/" [root entry])]
      {:tika-content (tika/parse fp)}))

  (access-denied-exception? [fs err]
    (instance? AccessDeniedException err))

  (get-permissions [fs entry]
    (let [fp (string/join "/" [root entry])]
      (if is-windows
        (windows-acl-from-path fp)
        (posix-acl-from-path fp))))

  (stat [fs entry]
    (let [fp (string/join "/" [root entry])
          attr (read-attributes fp)
          ct (fn [t] (/ (.toMillis t) 1000))]
      {:type (type-from-attribute attr)
       :size (.size attr)
       :mtime (ct (.lastModifiedTime attr))}))

  (join [fs parts]
    (normalize-path (string/join "/" parts)))

  (listdir [fs dir]
    (let [fp (string/join "/" [root dir])
          up (get-path fp)]
      (map #(-> % .getFileName str)
           (nio2.dir-seq/dir-seq up)))))


(defn tilde-expand-path
  [path]
  (cond
    (= path "~")
      (System/getProperty "user.home")
    (= "~/" (subs path 0 2))
      (str (System/getProperty "user.home") (subs path 1))
    :else
      path))


(defn filesystem-from-inisection
  [section]
  (let [path (section "path")]
    (when-not path
      (throw (Exception. "no path specified in section")))
    (RealFilesystem. (tilde-expand-path path))))


(def default-loadable
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this system section-name]
      (filesystem-from-inisection ((:iniconfig system) section-name)))))
