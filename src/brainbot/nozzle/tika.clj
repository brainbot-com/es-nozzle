(ns brainbot.nozzle.tika
  (:import [java.io InputStream]
           [org.apache.tika.metadata Metadata]
           [org.apache.tika Tika])
  (:require [clojure.string :as string])
  (:use [clojure.java.io :as io]))


(def ^Tika ^{:private true} tika-obj (Tika.))
(def ^{:dynamic true} *default-max-length* (.getMaxStringLength tika-obj))

(defn- wash
  "remove unicode 0xfffd character from string
  this is the unicode 'replacement character', which tika uses for
  unknown characters"
  [str]
  (string/trim (string/replace (or str "") (char 0xfffd) \space)))

(defn- conv-metadata [^Metadata mdata]
  (let [names (.names mdata)]
    (zipmap (map #(keyword (.toLowerCase %1)) names)
            (map #(seq (.getValues mdata %1)) names))))

(defn- parse-istream
  [^InputStream istream max-length]
  (let [metadata (Metadata.)
        text (wash (.parseToString tika-obj istream metadata (int max-length)))]
    (assoc (conv-metadata metadata) :text text)))


(defn parse
  ([in max-length]
     (parse-istream (io/input-stream in) max-length))
  ([in]
     (parse in *default-max-length*)))
