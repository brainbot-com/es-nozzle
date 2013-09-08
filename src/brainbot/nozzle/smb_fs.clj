(ns brainbot.nozzle.smb-fs
  (:require
   [clojure.string :as string])
  (:require [brainbot.nozzle.extract :refer [convert]])
  (:require [brainbot.nozzle.vfs :as vfs]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.misc :as misc])
  (:import [jcifs.smb SmbFile NtlmPasswordAuthentication SID ACE]))

(def ^:private group-like-types
  #{SID/SID_TYPE_ALIAS SID/SID_TYPE_DOM_GRP SID/SID_TYPE_WKN_GRP})


(defn- is-sid-group-like?
  [sid]
  (contains? group-like-types (.getType sid)))


(defn- smb-file-for-entry [fs entry]
  (SmbFile. (string/join [(:root fs) entry]) (:auth fs)))


(defn- convert-sid
  [sid]
  (let [name (.getAccountName sid)]
    (str (if (is-sid-group-like? sid)
           "GROUP"
           "USER")
         ":"
         name)))

(defn- convert-ace
  [ace]
  (if-not (zero? (bit-and (.getAccessMask ace) ACE/FILE_READ_DATA))
    (let [is-allow (.isAllow ace)
          sid (.getSID ace)]
      {:allow is-allow,
       :sid (convert-sid sid)})))


(defrecord SmbFilesystem [root auth]
  vfs/Filesystem
  (extract-content [fs entry]
    (let [smb-file (smb-file-for-entry fs entry)]
      (with-open [in (.getInputStream smb-file)]
        {:tika-content (convert in)})))


  (get-permissions [fs entry]
    (let [smb-file (smb-file-for-entry fs entry)
          acl (seq (.getSecurity smb-file true))]
      (remove nil? (map convert-ace acl))))

  (stat [fs entry]
    (let [smb-file (smb-file-for-entry fs entry)
          is-directory (.isDirectory smb-file)
          is-file (.isFile smb-file)
          common-result {:mtime (quot (.lastModified smb-file) 1000)
                         :type (cond
                                 is-directory
                                   :directory
                                 is-file
                                   :file
                                 :else
                                   :other)}]
      (if is-file
        (assoc common-result
          :size (.length smb-file))
        common-result)))

  (join [fs parts]
    (string/join "/" parts))

  (listdir [fs dir]
    (seq (.list (smb-file-for-entry fs (misc/ensure-endswith-slash dir))))))


(defn filesystem-from-inisection
  [section]
  (let [raise (fn [msg] (throw (Exception. msg)))
        domain (section "domain")
        username (section "username")
        path (section "path")
        password (section "password")]
    (when-not path
      (raise "path missing in section"))
    (when-not username
      (raise "username missing in section"))
    (when-not password
      (raise "password missing in section"))

    (->SmbFilesystem path (NtlmPasswordAuthentication. domain username password))))


(def default-loadable
  (reify
    brainbot.nozzle.dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section-name]
      (filesystem-from-inisection (iniconfig section-name)))))
