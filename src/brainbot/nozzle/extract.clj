(ns brainbot.nozzle.extract
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])
  (:require [clojure.data.codec.base64 :as base64])
  (:require [image-resizer.resize :refer [resize-fn]]
            [image-resizer.scale-methods :refer [ultra-quality]])
  (:require [clojure.tools.logging :as logging])
  (:require [brainbot.nozzle.mqhelper :as mqhelper]
            [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.thumbnail :as thumbnail]
            [brainbot.nozzle.sys :as sys]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.vfs :as vfs])
  (:require [brainbot.nozzle.tika :as tika]))


(defn extract-content
  [local-file-path entry]
  (if-let [converted (tika/parse local-file-path)]
    (assoc entry "tika-content" converted)
    entry))

(defn base64-string-from-img
  [img]
  (when img
    (let [os (java.io.ByteArrayOutputStream.)]
      (javax.imageio.ImageIO/write img "png" os)
      (-> os
          .toByteArray
          base64/encode
          String.))))

(defn remove-nil-values
  [m]
  (into {} (remove (comp nil? val) m)))

(defn make-thumbnail
  [extract fs path]
  (let [content-type (or (first (-> extract :tika-content :content-type)) "")
        thumbnail-img (thumbnail/make-thumbnail content-type #(vfs/get-input-stream fs path))]
    (base64-string-from-img thumbnail-img)))

(defn simple-extract_content
  [fs {directory :directory, {relpath :relpath :as entry} :entry, :as body} {publish :publish}]
  (let [path (vfs/join fs [directory relpath])
        extract (try
                  (vfs/extract-content fs path)
                  (catch Throwable err
                    (logging/error "error in extract-content"
                                   {:error err
                                    :path path
                                    :fsid (:fsid fs)})
                    nil))
        thumbnail (make-thumbnail extract fs path)
        new-body (merge body
                        (remove-nil-values {:extract extract
                                            :thumbnail thumbnail}))]
    (publish "import_file" new-body)))


(defn build-handle-connection
  [filesystems rmq-prefix num-workers]
  (fn [conn]
    (logging/info "initializing connection with" num-workers "workers")
    (dotimes [n num-workers]
      (doseq [{:keys [fsid] :as fs} filesystems]
        (mqhelper/channel-loop
         conn
         (fn [ch]
           (let [qname (mqhelper/initialize-rabbitmq-structures ch "extract_content" rmq-prefix fsid)]
             ;; (lb/qos ch 1)
             (lcons/subscribe ch qname (mqhelper/make-handler (partial simple-extract_content fs))))))))))


(defrecord ExtractService [rmq-settings rmq-prefix filesystems num-workers thread-pool]
  worker/Service
  (start [this]
    (future (mqhelper/connect-loop-with-thread-pool
             rmq-settings
             (build-handle-connection filesystems rmq-prefix num-workers)
             thread-pool))))

(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this system section]
      (let [rmq-settings (-> system :config :rmq-settings)
            rmq-prefix (-> system :config :rmq-prefix)
             num-workers (Integer. (get-in system [:iniconfig section "num-workers"] "10"))
            filesystems (map (partial vfs/make-filesystem system)
                             (sys/get-filesystems-for-section system section))]
        (->ExtractService rmq-settings rmq-prefix filesystems num-workers (:thread-pool system))))))
