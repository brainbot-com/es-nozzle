(ns brainbot.nozzle.dynaload
  "dynamic code loading")

(defprotocol Loadable
  "loadable thing")


(def registry
  (atom {"file" 'brainbot.nozzle.real-fs
         "smbfs" 'brainbot.nozzle.smb-fs

         "fsworker" 'brainbot.nozzle.manage/runner
         "meta" 'brainbot.nozzle.main/meta-runner
         "extract" 'brainbot.nozzle.extract2/runner
         "esconnect" 'brainbot.nozzle.esconnect/runner
         "manage" 'brainbot.nozzle.manage/runner

         "dotfile" 'brainbot.nozzle.fsfilter/dotfile
         "remove-extensions" 'brainbot.nozzle.fsfilter/remove-extensions}))


(defn- split-symbol-or-string
  [sym]
  (let [[nsname name] (clojure.string/split (str (@registry sym sym)) #"/")]
    [(symbol nsname) (symbol (or name "default-loadable"))]))


(defn- require-and-resolve
  [sym]
  (let [[ns-sym name-sym] (split-symbol-or-string sym)]
    (try
      (require ns-sym)
      (catch Exception err
        (throw (ex-info (format "could not load symbol %s: %s" sym err)
                        {:symbol sym}))))
    (or (ns-resolve (find-ns ns-sym) name-sym)
        (throw (ex-info (format "namespace %s does not have symbol %s" ns-sym name-sym)
                        {:symbol sym})))))

(defn get-loadable
  [sym]
  (let [loadable @(require-and-resolve sym)]
    (when-not (satisfies? Loadable loadable)
      (throw (ex-info "could not load symbol" {:symbol sym :error "not loadable"})))
    loadable))
