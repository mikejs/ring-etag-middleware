(ns ring.middleware.etag
  (:require [clojure.string :as string])
  (:import (java.security MessageDigest)
           (java.io File)))

(defn- to-hex-string [bytes]
  (string/join ""
   (map #(Integer/toHexString (bit-and % 0xff)) bytes)))

(defn- sha1 [obj]
   (let [bytes (.getBytes (with-out-str (pr obj)))] 
     (to-hex-string (.digest (MessageDigest/getInstance "SHA1") bytes))))

(def calculate-etag-dispatch-fn class)
(defmulti calculate-etag calculate-etag-dispatch-fn)
(defmethod calculate-etag String [s] (sha1 s))
(defmethod calculate-etag File
  [f]
  (str (.lastModified f) "-" (.length f)))

(defn- not-modified-response [etag]
  {:status 304 :body "" :headers {"etag" etag}})

(defn wrap-etag [handler]
  "Generates an etag header by hashing the response body.
   If the request's 'if-none-match' header matches, substitutes a
   304 response.

   You can add support for generating etags for a new body class
   using (defmethod calculate-etag <class>)."
  (fn [req]
    (let [{body :body
           status :status
           {etag "etag"} :headers
           :as resp} (handler req)
           if-none-match (get-in req [:headers "if-none-match"])
           dispatch-value (calculate-etag-dispatch-fn body)]
      (if (and etag (not= status 304))
        (if (= etag if-none-match)
          (not-modified-response etag)
          resp)
        (if-let [method-fn (get-method calculate-etag dispatch-value)]
          (let [etag (method-fn body)]
            (if (= etag if-none-match)
              (not-modified-response etag)
              (assoc-in resp [:headers "etag"] etag)))
          resp)))))
