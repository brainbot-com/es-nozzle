(ns brainbot.nozzle.version
  (:require [trptcolin.versioneer.core :as v]))


(defn enhanced-version
  "return version string with git revision appended if it's a snapshot"
  ([group artifact]
     (if-let [props (v/map-from-property-filepath
                     (v/get-properties-filename group artifact))]
       (let [version (get props "version")]
         (if (re-find #"SNAPSHOT" version)
           (str version "-" (subs (props "revision") 0 6))
           version))
       (System/getProperty (str artifact ".version"))))
  ([s]
     (let [[group artifact] (clojure.string/split s #"/" 2)]
       (if artifact
         (enhanced-version group artifact)
         (enhanced-version group group)))))  ;; some project do that! e.g. clj-time



(defn nozzle-version
  []
  (enhanced-version "com.brainbot" "nozzle"))
