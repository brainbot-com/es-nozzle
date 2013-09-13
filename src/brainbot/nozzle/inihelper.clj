(ns brainbot.nozzle.inihelper
  (:require [com.brainbot.iniconfig :as ini])
  (:require [langohr.core :as rmq])
  (:require [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.misc :as misc]))

(def main-section-name "nozzle")

(defn get-filesystems-from-iniconfig
  [iniconfig section]
  (misc/trimmed-lines-from-string
   (or (get-in iniconfig [section "filesystems"])
       (get-in iniconfig [main-section-name "filesystems"]))))

(def default-ini-config
  (-> "META-INF/brainbot.nozzle/default-config.ini"
      clojure.java.io/resource
      ini/read-ini))

(defn rmq-settings-from-config
  [iniconfig]
  (rmq/settings-from (get-in iniconfig [main-section-name "amqp-url"])))

(defn merge-with-default-config
  "merge cfg with default-ini-config, keep cfg's metadata"
  [cfg]
  (with-meta
    (merge default-ini-config cfg)
    (meta cfg)))


(defn read-ini-with-defaults
  [inifile]
  (-> inifile ini/read-ini merge-with-default-config))


(defprotocol IniConstructor
  (make-object-from-section [this system section-name]))


(def registry
  (atom
   {"file" 'brainbot.nozzle.real-fs
    "smbfs" 'brainbot.nozzle.smb-fs

    "fsworker" 'brainbot.nozzle.fsworker/runner
    "meta" 'brainbot.nozzle.meta-runner/runner
    "extract" 'brainbot.nozzle.extract2/runner
    "esconnect" 'brainbot.nozzle.esconnect/runner
    "manage" 'brainbot.nozzle.manage/runner

    "dotfile" 'brainbot.nozzle.fsfilter/dotfile
    "extensions" 'brainbot.nozzle.fsfilter/extensions-filter-constructor}))


(defn dynaload-section
  [system section-name]

  (let [iniconfig (:iniconfig system)
        type (get-in iniconfig [section-name "type"])]
    (when-not type
      (throw (ex-info (format "no type defined in section %s" section-name) {})))

    (let [loadable (dynaload/get-loadable (@registry type type))]
      (when-not (satisfies? IniConstructor loadable)
        (throw (ex-info "bad loadable" {:section-name section-name :type type})))
      (with-meta
        (make-object-from-section loadable system section-name)
        {:type type
         :section-name section-name}))))


(defn ensure-protocol
  [protocol x]
  (when-not (satisfies? protocol x)
    (throw (ex-info
            (format "wrong type in section %s, expected %s, got %s"
                    (:section-name (meta x))
                    (:on-interface protocol)
                    (class x))
            {:obj x})))
  x)
