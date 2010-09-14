(ns ring.middleware.etag
  (:require [clojure.contrib.str-utils :as str-utils])
  (:import (java.security MessageDigest)))

(defn- to-hex-string [bytes]
  (str-utils/str-join "" (map #(Integer/toHexString (bit-and % 0xff))
                              bytes)))

(defn- sha1 [obj]
   (let [bytes (.getBytes (with-out-str (pr obj)))] 
     (to-hex-string (.digest (MessageDigest/getInstance "SHA1") bytes))))

(defn wrap-etag [handler]
  (fn [req]
    (let [resp (handler req)
          body (resp :body)
          etag ((resp :headers) "etag")]
      (if (and etag (not= (resp :status) 304))
        (if (= etag ((req :headers) "if-none-match"))
          {:status 304 :headers {"etag" etag}}
          resp)
        (if (string? body)
          (let [etag (str "\"" (sha1 body) "\"")
                headers (assoc (resp :headers) "etag" etag)]
            (if (= etag ((req :headers) "if-none-match"))
              {:status 304 :headers {"etag" etag}}
              (assoc resp :headers headers)))
          resp)))))