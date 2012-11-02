(ns ring.middleware.etag-test
  (:require [clojure.java.io :as io])
  (:use clojure.test
        ring.middleware.etag)
  (:import java.net.URL
           java.io.File))

(def str-resp (wrap-etag (fn [req] {:status 200 :body "content" :headers {}})))
(def file-resp
  (wrap-etag (fn [req]
               (let [url (io/resource "test.txt")
                     file (io/as-file url)]
                 {:status 200, :body file}))))

;; URL isn't actually legal per Ring spec, but this is just an example.
;; Ring does allow ISeq, for example, so you might define a method for those.
(defmethod calculate-etag URL [url]
  (calculate-etag (io/as-file url)))
(def url-resp
  (wrap-etag (fn [req] {:status 200, :body (io/resource "test.txt")})))

(defn get-etag
  [resp]
  (get-in resp [:headers "etag"]))

(deftest test-etag
  (doseq [app [str-resp file-resp url-resp]]
    (let [resp (app {:headers {}})
          etag (get-etag resp)
          body (:body resp)]
      (is (= 200 (:status resp)))
      (when (instance? String body)
        (is (= "content" body)))
      (when (instance? File body)
        (is (= "content" (slurp body))))
      (is (not (nil? etag)))
      (let [resp (app {:headers {"if-none-match" etag}})]
        (is (= 304 (:status resp)))
        (is (empty? (:body resp)))
        (is (= etag (get-etag resp)) "etag"))
      (let [resp (app {:headers {"if-none-match" "not-the-etag"}})]
        (is (= 200 (:status resp)))
        (when (instance? String body)
          (is (= "content" body)))
        (when (instance? File body)
          (is (= "content" (slurp body))))
        (is (= etag (get-etag resp)))))))

(deftest test-wrapped-etag
  (let [app (wrap-etag (fn [req] {:status 200 :body "content"
                                  :headers {"etag" "an-etag"}}))
        resp (app {:headers {}})
        etag ((resp :headers) "etag")]
    (is (= 200 (:status resp)))
    (is (= "content" (:body resp)))
    (is (= "an-etag" etag))
    (let [resp (app {:headers {"if-none-match" "an-etag"}})]
      (is (= 304 (:status resp))))
    (let [resp (app {:headers {"if-none-match" "not-the-etag"}})]
      (is (= 200 (:status resp))))))
