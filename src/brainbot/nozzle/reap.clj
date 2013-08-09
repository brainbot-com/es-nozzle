(ns brainbot.nozzle.reap
  (:import [java.util.concurrent TimeUnit ScheduledThreadPoolExecutor Callable])
  (:require [clojure.stacktrace :as trace])
  (:require [clojure.tools.logging :as logging]))


(def ^:private scheduled-executor (ScheduledThreadPoolExecutor. 4))


(defn- periodically
  "periodically run function f with a fixed delay of n milliseconds"

  [n f]
  (.scheduleWithFixedDelay scheduled-executor ^Runnable f 0 n TimeUnit/MILLISECONDS))


(def ^:private future-map (atom {}))


(defn register-future!
  "register future, on-error will be called if the future exits with an exception, otherwise
  on exit will be called"
  [a-future on-error on-exit]
  (swap! future-map assoc a-future {:on-error on-error :on-exit on-exit}))


(defn- unregister-future!
  [a-future]
  (swap! future-map dissoc a-future))


(defn- deref-exception
  [a-future]
  (try
    (do
      @a-future
      nil)
    (catch Throwable err
      err)))

(def ^:private call-counter (atom 0))


(defn- mark-watcher-alive
  "print a log message each minute, so we know the reaper is still
 alive"
  []
  (swap! call-counter inc)
  (when (zero? (rem @call-counter 240))
    (logging/info "reaping futures" @call-counter)))


(defn- watch-futures
  "watch a sequence of futures, call on-error if a future exited with an exception,
   call on-exit if it exited normally"
  []
  (try
    (mark-watcher-alive)
    ;; (println 'watch-futures get-futures on-error on-exit)
    (doseq [[a-future callbacks] @future-map]
      (when (realized? a-future)
        (unregister-future! a-future)
        (let [do-nothing (constantly nil)
              on-error (or (:on-error callbacks) do-nothing)
              on-exit (or (:on-exit callbacks) do-nothing)
              error (deref-exception a-future)]
          (if error
            (on-error a-future error)
            (on-exit a-future nil)))))
    (catch Throwable err
      (trace/print-stack-trace err)
      (logging/error "an error occured in watch-futures" err))))


(defn start-watching-futures!
  "start watching futures registered with register-future!"
  []
  (periodically 250 watch-futures))
