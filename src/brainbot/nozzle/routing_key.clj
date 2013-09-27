(ns brainbot.nozzle.routing-key
  "handle routing keys as used in RabbitMQ. These are also used as queue names.
A routing key looks like

ID.FILESYSTEM.COMMAND

where ID is the rmq-prefix in the [es-nozzle] section FILESYSTEM is
the filesystem and COMMAND is the command being worked on.
"
  (:require [clojure.string :as string]))

(defn map-from-routing-key-string
  "build map from routing key"
  [rk-string]
  (zipmap [:id :filesystem :command] (string/split rk-string #"\.")))

(defn routing-key-string
  "make routing key string from single map or parameters"
  ([{:keys [id filesystem command]}]
     (string/join "." [id filesystem command]))
  ([id filesystem command]
     (string/join "." [id filesystem command])))

(defn routing-key-string-with-command
  "replace command part of routing key string with command"
  [rk-string command]
  (routing-key-string (assoc (map-from-routing-key-string rk-string) :command command)))
