(ns brainbot.nozzle.vfs
  (:require [clojure.tools.logging :as logging])
  (:require
   brainbot.nozzle.dynaload
   [brainbot.nozzle.inihelper :as inihelper]
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


(def dynaload-filesystem
  (comp
   (partial inihelper/ensure-protocol Filesystem)
   inihelper/dynaload-section))


(defn make-single-filesystem-from-iniconfig
  "create filesystem from ini config section"
  [iniconfig section-name]
  (let [fs (dynaload-filesystem iniconfig section-name)]
    (assoc fs
      :fsid section-name)))


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
