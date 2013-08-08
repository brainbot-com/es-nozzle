(ns fscrawler-tika-convert.extract
  (:require [clojure.stacktrace :as trace])

  (:require [fscrawler-tika-convert [reap :as reap] [core :as core] [misc :as misc]]
            [fscrawler-tika-convert.misc :refer [die]]))

(defn die-on-exit-or-error
  "print stack trace and die, used as callback to register-future!"
  [a-future error]
  (when error
    (trace/print-stack-trace error))
  (die "thread died unexpectedly"))


(defn extract-options-from-iniconfig
  [iniconfig section]
  (let [source (:source (meta iniconfig))
        fscrawler-section (or (iniconfig "fscrawler") {})
        max-size (Integer. (fscrawler-section "max-size")),
        amqp-url (get fscrawler-section "amqp-url" "amqp://localhost/%2f")
        filesystems (misc/get-filesystems-from-iniconfig iniconfig section)]
    (when (zero? (count filesystems))
      (die (str "no filesystems defined in section " section " in " source)))
    {:max-size max-size
     :amqp-url amqp-url
     :filesystems filesystems}))


(defn extract-run-section
  [iniconfig section]
  (let [{:keys [filesystems] :as options} (extract-options-from-iniconfig
                                           iniconfig section)]
    (reap/start-watching-futures!)

    (doseq [filesystem filesystems]
      (reap/register-future! (future (core/handle-command-for-filesystem-forever filesystem options))
                             die-on-exit-or-error die-on-exit-or-error))))
