(ns brainbot.nozzle.thumbnail
  "thumbnail generation"
  (:require [clojure.data.codec.base64 :as base64])
  (:require [image-resizer.resize :refer [resize-fn]]
            [image-resizer.scale-methods :refer [ultra-quality]]))

(defn base64-png-from-img
  [img]
  (let [os (java.io.ByteArrayOutputStream.)]
    (javax.imageio.ImageIO/write img "png" os)
    (base64/encode (.toByteArray os))))

(defn thumbnail-from-image
  [img]
  (let [resize (resize-fn 75 75 ultra-quality)]
    (-> img
        resize)))

(defn read-image [get-input-stream]
  (with-open [in (get-input-stream)]
    (javax.imageio.ImageIO/read in)))

(defmulti make-thumbnail
  (fn [content-type get-input-stream]
    (cond
      (nil? content-type)
        nil
      (re-find #"^image/" content-type)
        :image
      :else
        content-type)))

(defmethod make-thumbnail :default
  [content-type get-input-stream]
  nil)

(defmethod make-thumbnail :image
  [content-type get-input-stream]
  (some-> get-input-stream read-image thumbnail-from-image))

;; (org.jpedal.fonts.FontMappings/setFontReplacements)

(defn get-pdf-preview-image
  [get-input-stream]
  (with-open [in (get-input-stream)]
    (let [pdf-decoder (org.jpedal.PdfDecoder.)]
      (try
        (.setExtractionMode pdf-decoder 0)
        (.openPdfFileFromInputStream pdf-decoder in false)
      (.getPageAsImage pdf-decoder 1)
      (finally
        (.closePdfFile pdf-decoder))))))

(defmethod make-thumbnail "application/pdf"
  [content-type get-input-stream]
  (-> get-input-stream get-pdf-preview-image thumbnail-from-image))
