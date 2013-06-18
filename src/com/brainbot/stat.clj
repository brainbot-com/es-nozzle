(ns com.brainbot.stat
  (:import [java.io File IOException FileNotFoundException]
           [java.nio.file Files Path LinkOption]
           [java.nio.file.attribute PosixFilePermissions BasicFileAttributes PosixFileAttributes]))

(def no-follow-links
  (into-array [LinkOption/NOFOLLOW_LINKS]))


(defn- get-path
  [path]
  (.toPath (File. path)))


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
