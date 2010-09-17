(ns ring.middleware.etag
  (:require [clojure.contrib.str-utils :as str-utils])
  (:import (java.security MessageDigest)
           (java.io File)))

(defn- to-hex-string [bytes]
  (str-utils/str-join "" (map #(Integer/toHexString (bit-and % 0xff))
                              bytes)))

(defn- sha1 [obj]
   (let [bytes (.getBytes (with-out-str (pr obj)))] 
     (to-hex-string (.digest (MessageDigest/getInstance "SHA1") bytes))))

(defmulti calculate-etag class)
(defmethod calculate-etag String [s] (sha1 s))
(defmethod calculate-etag File
  [f]
  (str (.lastModified f) "-" (.length f)))

(defn- not-modified-response [etag]
  {:status 304 :body "" :headers {"etag" etag}})

(defn wrap-etag [handler]
  "Generates an etag header by hashing response body (currently only
supported for string bodies). If the request includes a matching
'if-none-match' header then return a 304."
  (fn [req]
    (let [{body :body
           status :status
           {etag "etag"} :headers
           :as resp} (handler req)
           if-none-match (get-in req [:headers "if-none-match"])]
      (if (and etag (not= status 304))
        (if (= etag if-none-match)
          (not-modified-response etag)
          resp)
        (if (or (string? body) (instance? body File))
          (let [etag (calculate-etag body)]
            (if (= etag if-none-match)
              (not-modified-response etag)
              (assoc-in resp [:headers "etag"] etag)))
          resp)))))