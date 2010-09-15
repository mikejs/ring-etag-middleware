(ns ring.middleware.etag-test
  (:use clojure.test
        ring.middleware.etag))

(def app (wrap-etag (fn [req] {:status 200 :body "content" :headers {}})))

(deftest test-etag
  (let [resp (app {:headers {}})
        etag ((resp :headers) "etag")]
    (is (= 200 (:status resp)))
    (is (= "content" (:body resp)))
    (is (not (nil? etag)))
    (let [resp (app {:headers {"if-none-match" etag}})]
      (is (= 304 (:status resp)))
      (is (empty? (:body resp)))
      (is (= etag ((resp :headers) "etag"))))
    (let [resp (app {:headers {"if-none-match" "not-the-etag"}})]
      (is (= 200 (:status resp)))
      (is (= "content" (:body resp)))
      (is (= etag ((resp :headers) "etag"))))))

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