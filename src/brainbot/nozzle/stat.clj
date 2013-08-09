(ns brainbot.nozzle.stat
  (:import [java.io File IOException FileNotFoundException]
           [java.nio.file Files Path LinkOption Paths]
           [java.nio.file.attribute AclFileAttributeView PosixFilePermissions PosixFilePermission BasicFileAttributes PosixFileAttributes]))


(def no-follow-links
  (into-array [LinkOption/NOFOLLOW_LINKS]))


(def posix-file-permission->st-mode
  {PosixFilePermission/OWNER_READ       00400
   PosixFilePermission/OWNER_WRITE      00200
   PosixFilePermission/OWNER_EXECUTE    00100

   PosixFilePermission/GROUP_READ       00040
   PosixFilePermission/GROUP_WRITE      00020
   PosixFilePermission/GROUP_EXECUTE    00010

   PosixFilePermission/OTHERS_READ      00004
   PosixFilePermission/OTHERS_WRITE     00002
   PosixFilePermission/OTHERS_EXECUTE   00001})


(def type->st-mode
  {:directory           0040000
   :file                0100000
   :symbolic-link       0120000
   :other               0060000})   ;; that's a S_IFBLK, i.e. a block device

(defn st-mode-from-type-and-permissions
  [type permissions]
  (apply bit-or
         (concat
          [0 (type->st-mode type)]
          (map posix-file-permission->st-mode permissions))))


(defn- get-path
  [path & args]
  (Paths/get path (into-array String args)))

(defn stat
  [path]
  (let [attr (Files/readAttributes (get-path path) PosixFileAttributes no-follow-links)
        ct (fn [t] (/ (.toMillis t) 1000))]
    {:size (.size attr)
     :type (cond
             (.isDirectory attr)
               :directory
             (.isRegularFile attr)
               :file
             (.isSymbolicLink attr)
               :symbolic-link
             :else
               :other)
     :permissions (.permissions attr)
     :owner-name (.getName (.owner attr))
     :group-name (.getName (.group attr))
     :creation-time (ct (.creationTime attr))
     :last-access-time (ct (.lastAccessTime attr))
     :last-modified-time (ct (.lastModifiedTime attr))}))


(defn read-windows-acls
  [path]
  (-> path
      get-path
      (Files/getFileAttributeView AclFileAttributeView (into-array LinkOption []))
      .getAcl
      seq))
