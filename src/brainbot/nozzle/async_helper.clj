(ns brainbot.nozzle.async-helper
  "some small helpers for core.async"
  (:require [clojure.core.async :refer [go thread chan mult put! close! <! <!! >! >!!] :as async]))


(defn- call-and-put
  [f ch]
  (go
   (let [r (<! (f))]
     (when-not (or (nil? r) (nil? ch))
       (>! ch r)))))

(defn looping-go
  "call f in a loop from a go block. f must return a channel, from
which we read one message, put the result on dest-ch if given. sleep
ms milliseconds before calling f again"
  [ms f & {:keys [start]}]
  (assert (ifn? f) "f must be a function")
  (let [ctrl-ch (chan)
        dest-ch (chan)]
    (go
     (try
       (when (<! ctrl-ch)
         (loop []
           (<! (call-and-put f dest-ch))
           (async/alt!
            ctrl-ch ([v] (when-not (nil? v) (recur)))
            (async/timeout ms) ([v] (recur)))))
       (finally
         (when-not (nil? dest-ch)
           (close! dest-ch)))))
    (when start
      (put! ctrl-ch true))
    {::ctrl-ch ctrl-ch :dest-ch dest-ch}))

(defn looping-stop
  "stop function calling loop"
  [{ctrl-ch ::ctrl-ch}]
  (async/close! ctrl-ch))

(defn looping-start
  [{ctrl-ch ::ctrl-ch}]
  (put! ctrl-ch :start))
