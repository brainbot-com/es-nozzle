(ns brainbot.nozzle.dynaload
  "dynamic code loading")

(defprotocol Loadable
  "loadable thing")


(defn- split-symbol-or-string
  [sym]
  (let [[nsname name] (clojure.string/split (str sym) #"/")]
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
