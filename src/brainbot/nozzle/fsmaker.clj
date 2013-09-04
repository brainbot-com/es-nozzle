(ns brainbot.nozzle.fsmaker)

(defprotocol FilesystemBuilder
  (make-filesystem-from-iniconfig
    [this iniconfig section-name]
    "create filesystem from ini configuration"))
