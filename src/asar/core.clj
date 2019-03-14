(ns asar.core
  "Read ASAR archive"
  (:require [clojure.data.json :as json]
            [clojure.string :as str])
  (:import (java.io File RandomAccessFile)
           (java.nio.channels Channels WritableByteChannel)
           (java.io OutputStream)))

(set! *warn-on-reflection* true)

(defrecord ASAR [file header offset])

(defn- little-endian->big-endian [num]
  (bit-or (bit-and (bit-shift-right num 24) 0xff)
          (bit-and (bit-shift-left num 8) 0xff0000)
          (bit-and (bit-shift-right num 8) 0xff00)
          (bit-and (bit-shift-left num 24) 0xff000000)))

(defn transform-header
  "Turn header into a mapping from file path to size and offset."
  ([header] (transform-header "" header))
  ([path {files "files" :as header}]
   (into {}
         (mapcat (fn [[name file-info]]
                   (if (contains? file-info "files")
                     ;; This is a directory
                     (transform-header (str path name "/") file-info)

                     ;; This is a regular file
                     [[(str path name) {:size (file-info "size")
                                         :offset (Long/parseLong (file-info "offset"))}]])))
         files)))

(defn- read-header [{^RandomAccessFile file :file :as asar}]
  (.seek file 4)
  (let [header-size-le (.readInt file)
        header-size (little-endian->big-endian header-size-le)
        header-bytes (byte-array (- header-size 8))]
    (.skipBytes file 8)
    (.readFully file header-bytes)
    (assoc asar
           :header (transform-header (json/read-str (String. header-bytes "UTF-8")))
           :offset (+ header-size 8))))

(defn load-asar
  "Load an ASAR archive and read the header. Returns an ASAR record instance
  that can be used to access the files contained in the archive."
  [^File file]
  {:pre [(instance? File file)]}
  (-> (RandomAccessFile. file "r")
      (->ASAR nil nil)
      (read-header)))

(defn read-file
  "Read the contents of the file denoted by path. Returns a byte array
  with the contents or nil if the file is not found in the archive."
  [{:keys [header ^RandomAccessFile file offset]} path]
  (if-let [{size :size
            file-offset :offset} (get header path)]
    (let [file-bytes (byte-array size)]
      (.seek file (+ offset file-offset))
      (.readFully file file-bytes)
      file-bytes)
    nil))

(defn copy
  "Copy the contents of the file denote by path to the given output stream."
  [{:keys [header ^RandomAccessFile file offset]} path ^OutputStream to]
  (if-let [{size :size file-offset :offset} (get header path)]
    (with-open [^WritableByteChannel out-chan (Channels/newChannel to)]
      (-> file .getChannel (.transferTo (+ file-offset offset) size out-chan)))
    (throw (ex-info "File not found"
                    {:path path}))))

(defn list-files
  "List the contents of the asar archive."
  [{:keys [header]}]
  (keys header))

(defn file-info
  "Get info on the file denoted by path."
  [{:keys [header]} path]
  (get header path))

(defonce piped-input-stream (delay (require '[ring.util.io])
                                   (resolve 'ring.util.io/piped-input-stream)))

(defn ring-handler
  "Returns a ring handler for serving files from an ASAR archive."
  [^File asar-file path]
  (let [asar (load-asar asar-file)]
    (fn [{uri :uri :as req}]
      (when (str/starts-with? uri path)
        (let [file-path (subs uri (count path))]
          (when-let [{size :size} (file-info asar file-path)]
            {:headers {"Content-Length" size}
             :body (@piped-input-stream
                    (fn [^OutputStream out]
                      (copy asar file-path out)))}))))))
