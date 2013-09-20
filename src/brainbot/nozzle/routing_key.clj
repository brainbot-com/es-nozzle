(ns brainbot.nozzle.routing-key
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

