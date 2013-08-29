(ns brainbot.nozzle.vfs
  (:require [clojure.tools.logging :as logging])
  (:require
   [brainbot.nozzle.misc :as misc]
   [clojure.string :as string]))


(defprotocol Filesystem
  "filesystem"
  (access-denied-exception? [fs exc] "access denied?")
  (extract-content [fs entry] "extract content from file")
  (get-permissions [fs entry] "get permissions")
  (stat [fs path] "stat entry")
  (join [fs parts] "join parts")
  (listdir [fs dir] "list directory"))

(def ^:private filesystem-registry
  (atom {"file" 'brainbot.nozzle.real-fs
         nil 'brainbot.nozzle.real-fs
         "smbfs" 'brainbot.nozzle.smb-fs}))


;; (defn register-filesystem
;;   [type create-filesystem-fn]
;;   (swap! filesystem-registry assoc type create-filesystem-fn))


(defn- load-fs-package
  [name]
  (binding [*ns* (create-ns 'brainbot.nozzle.vfs.tmp)]
    (try
      (require [name :as 'mod] :reload)
      (resolve 'mod/filesystem-from-inisection)
      (catch java.io.FileNotFoundException err
        nil)
      (finally
        (remove-ns 'brainbot.nozzle.vfs.tmp)))))


(defn- get-create-fs-fn
  [fstype]
  (let [create-fs (@filesystem-registry fstype)]
    (cond
      (nil? create-fs)
        (load-fs-package (symbol fstype))
      (symbol? create-fs)
        (load-fs-package create-fs)
      :else
        create-fs)))


(defn make-single-filesystem-from-iniconfig
  "create filesystem from ini config section"
  [iniconfig section-name]
  (let [section (iniconfig section-name)
        fstype (get section "type")]
    (when-not section
      (throw (ex-info (format "no filesystem %s declared, section missing" section-name) {:section-name section-name})))
    (if-let [create-fs (get-create-fs-fn fstype)]
      (assoc (create-fs section) :fsid section-name)
      (throw (ex-info (str "unknown filesystem type " fstype) {:section-name section-name :fstype fstype})))))


(defn make-filesystems-from-iniconfig
  "create a sequence of the filesystems specified in `section` with
   the filesystems key"
  [iniconfig section]
  (map (partial make-single-filesystem-from-iniconfig iniconfig)
       (misc/get-filesystems-from-iniconfig iniconfig section)))

(defn- listdir-catch-access-denied
  "call listdir, catch access denied errors, log an error for them and
  pretend the directory is empty"
  [fs path]
  (try
    (listdir fs path)
    (catch Exception err
      (if (access-denied-exception? fs err)
        (do
          (logging/info "access denied in listdir" {:fsid (:fsid fs), :path path})
          [])
        (throw err)))))


(defn make-safe-stat-entry
  [fs path]
  (fn [entry]
    (try
      {:relpath entry
       :stat (stat fs (join fs [path entry]))}
      (catch Exception err
        {:relpath entry
         :error (str err)}))))


(defn cmd-listdir
  [fs path]
  (map (make-safe-stat-entry fs path)
       (listdir-catch-access-denied fs path)))
