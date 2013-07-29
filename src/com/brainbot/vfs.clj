(ns com.brainbot.vfs
  (:require
   [clojure.string :as string]))


(defprotocol Filesystem
  "filesystem"
  (get-permissions [fs entry] "get permissions")
  (stat [fs path] "stat entry")
  (join [fs parts] "join parts")
  (listdir [fs dir] "list directory"))

(def ^:private filesystem-registry
  (atom {"file" 'com.brainbot.real-fs
         "smbfs" 'com.brainbot.smb-fs}))


;; (defn register-filesystem
;;   [type create-filesystem-fn]
;;   (swap! filesystem-registry assoc type create-filesystem-fn))


(defn- load-fs-package
  [name]
  (binding [*ns* (create-ns 'com.brainbot.vfs.tmp)]
    (try
      (require [name :as 'mod] :reload)
      (resolve 'mod/filesystem-from-inisection)
      (catch java.io.FileNotFoundException err
        nil)
      (finally
        (remove-ns 'com.brainbot.vfs.tmp)))))


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


(defn filesystem-from-inisection
  "create filesystem from ini config section"
  [section]
  (if-let [fstype (section "type")]
    (if-let [create-fs (get-create-fs-fn fstype)]
      (create-fs section)
      (throw (Exception. (str "unknown filesystem type " fstype))))
    (throw (Exception. (str "no type specified in section")))))


(defn cmd-listdir
  [fs path]
  (map (fn [entry]
         {:relpath entry
          :stat (stat fs (join fs [path entry]))}) ;; XXX error handling
       (listdir fs path)))
