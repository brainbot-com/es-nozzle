(ns brainbot.nozzle.dev
  "namespace used for interactive development with the repl"
  (:require [brainbot.nozzle.inihelper :as inihelper]))

(def iniconfig
  (inihelper/read-ini-with-defaults
   (format "%s/doc/nozzle.ini" (System/getProperty "user.dir"))))
