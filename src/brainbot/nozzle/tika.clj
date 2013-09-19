(ns brainbot.nozzle.tika
  "extract text from documents with the tika library"
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

(defn- lower-case-keyword
  "create lower-cased keyword from string"
  [s]
  (-> s string/lower-case keyword))

(defn- metadata-as-map
  "convert Tika Metadata to map with lower-cased keywords as keys"
  [^Metadata mdata]
  (let [names (.names mdata)
        values-from-metadata (fn [^String n]
                               (seq (.getValues mdata n)))]
    (zipmap (map lower-case-keyword names)
            (map values-from-metadata names))))

(defn- parse-istream
  [^InputStream istream max-length]
  (let [metadata (Metadata.)
        text (wash (.parseToString tika-obj istream metadata (int max-length)))]
    (assoc (metadata-as-map metadata) :text text)))


(defn parse
  "try to coerce in to an input-stream and parse it with tika
  extracting at most max-length characters."
  ([in max-length]
     (parse-istream (io/input-stream in) max-length))
  ([in]
     (parse in *default-max-length*)))
