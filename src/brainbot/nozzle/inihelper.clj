(ns brainbot.nozzle.inihelper
  (:require [brainbot.nozzle.dynaload :as dynaload]))


(defprotocol IniConstructor
  (make-object-from-section [this iniconfig section-name]))


(def registry
  (atom
   {"file" 'brainbot.nozzle.real-fs
    "smbfs" 'brainbot.nozzle.smb-fs

    "fsworker" 'brainbot.nozzle.fsworker/runner
    "meta" 'brainbot.nozzle.main/meta-runner
    "extract" 'brainbot.nozzle.extract2/runner
    "esconnect" 'brainbot.nozzle.esconnect/runner
    "manage" 'brainbot.nozzle.manage/runner

    "dotfile" 'brainbot.nozzle.fsfilter/dotfile
    "remove-extensions" 'brainbot.nozzle.fsfilter/remove-extensions}))


(defn dynaload-section
  [iniconfig section-name]
  (let [type (get-in iniconfig [section-name "type"])]
    (when-not type
      (throw (ex-info (format "no type defined in section %s" section-name) {})))

    (let [loadable (dynaload/get-loadable (@registry type type))]
      (when-not (satisfies? IniConstructor loadable)
        (throw (ex-info "bad loadable" {:section-name section-name :type type})))
      (with-meta
        (make-object-from-section loadable iniconfig section-name)
        {:type type
         :section-name section-name}))))


(defn ensure-protocol
  [protocol x]
  (when-not (satisfies? protocol x)
    (throw (ex-info
            (format "wrong type in section %s, expected %s" (:section-name (meta x)) protocol)
            {})))
  x)
