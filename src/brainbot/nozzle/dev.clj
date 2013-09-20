(ns brainbot.nozzle.dev
  "namespace used for interactive development with the repl"
  (:require [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.sys :as sys]))

(def iniconfig
  (inihelper/read-ini-with-defaults
   (format "%s/doc/es-nozzle.ini" (System/getProperty "user.dir"))))

(def system (sys/make-system iniconfig []))
