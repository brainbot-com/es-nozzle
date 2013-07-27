(ns com.brainbot.vfs
  (:require
   [clojure.string :as string]))



(defprotocol Filesystem
  "filesystem"
  (get-permissions [fs entry] "get permissions")
  (stat [fs path] "stat entry")
  (join [fs parts] "join parts")
  (listdir [fs dir] "list directory"))




(defn cmd-listdir
  [fs path]
  (map (fn [entry]
         {:relpath entry
          :stat (stat fs (join fs [path entry]))}) ;; XXX error handling
       (listdir fs path)))
