(ns brainbot.nozzle.routing-key
  (:require [clojure.string :as string]))

(defn map-from-routing-key-string
  "build map from routing key"
  [rk-string]
  (zipmap [:id :filesystem :command] (string/split rk-string #"\.")))


(defn routing-key-string-from-map
  "build routing key string from map"
  [{:keys [id command filesystem]}]
  (string/join "." [id filesystem command]))


(defn routing-key-string-with-command
  "replace command part of routing key string with command"
  [rk-string command]
  (routing-key-string-from-map (assoc (map-from-routing-key-string rk-string) :command command)))
