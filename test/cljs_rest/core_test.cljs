(ns cljs-rest.core-test
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cljs-rest.core :refer [async->]])
  (:require [clojure.string :as string]
            [cljs.test :refer-macros [async deftest is]]
            [cljs.core.async :refer [<!]]
            [cljs-rest.core :as rest]))

(def listing-url "http://localhost:4000/entries/")

(defn item-url [n]
  (str listing-url n "/"))

(def listing
  (rest/resource-listing listing-url))

(def payloads
  [{:a "b" :c "d"}
   {:a "b"}])

(defrecord Foo [a])

(defn constructor [data]
  (reduce
    (fn [acc [k v]]
      (assoc acc k (if (string? v) (string/upper-case v) v)))
    (map->Foo {})
    data))

(deftest listing-head
  (async done
    (go
      (let [head (<! (rest/head listing))]
        (is (map? head))
        (is (= "0" (get-in head [:data :content-length])))
        (done)))))

(deftest listing-options
  (async done
    (go
      (let [options (<! (rest/options listing))
            alt (<! (rest/options options))
            expected (rest/resource-options listing-url
                       :ok? true
                       :data {:name "Entries"})]
        (is (= options alt expected))
        (done)))))

(deftest listing-create
  (async done
    (go
      (let [payload (first payloads)
            resource (<! (rest/create! listing payload))
            resource-url (item-url 1)
            expected (rest/resource resource-url
                       :ok? true
                       :data (assoc payload :url resource-url))]
        (is (= resource expected))
        (done)))))

(deftest listing-read
  (async done
    (go
      (let [resources (<! (rest/read listing))
            url-1 (item-url 1)
            data-1 (assoc (first payloads) :url url-1)
            expected (list
                       (rest/resource url-1
                         :ok? true
                         :data data-1))]
        (is (= (:data resources) expected))
        (done)))))

(deftest listing-read-params
  (async done
    (go
      (let [resources (<! (rest/read listing {:empty "results"}))
            expected (list)]
        (is (= (:data resources) (list)))
        (done)))))

(deftest listing-error
  (async done
    (go
      (let [listing (rest/resource-listing "http://localhost:4000/does-not-exist/")
            resources (<! (rest/read listing))]
        (is (= false (:ok? resources)))
        (is (= 404 (get-in resources [:data :status])))
        (done)))))

(deftest listing-first-item
  (async done
    (go
      (let [resource (<! (rest/first-item listing))
            url-1 (item-url 1)
            data-1 (assoc (first payloads) :url url-1)
            expected (rest/resource url-1
                       :ok? true
                       :data data-1)]
        (is (= expected resource))
        (done)))))

(deftest listing-first-item-empty-error
  (async done
    (go
      (let [resources (<! (rest/first-item listing {:empty "results"}))]
        (is (= false (:ok? resources)))
        (is (= 404 (get-in resources [:data :status])))
        (done)))))

(deftest listing-first-item-empty-error-handler
  (async done
    (go
      (let [error-handled (atom nil)
            error-handler (fn [res] (reset! error-handled res))
            with-error-handler (assoc-in listing [:opts :error-handler] error-handler)
            resources (<! (rest/first-item with-error-handler {:empty "results"}))]
        (is (= 404 (:status @error-handled)))
        (done)))))

(deftest listing-first-item-empty-default-error-handler
  (async done
    (go
      (let [error-handled (atom nil)
            error-handler (fn [res] (reset! error-handled res))]
        (binding [rest/*opts* (assoc rest/*opts* :error-handler error-handler)]
          (let [resources (<! (rest/first-item listing {:empty "results"}))]
            (is (= 404 (:status @error-handled)))
            (done)))))))

(deftest listing-create-construction
  (async done
    (go
      (let [listing* (assoc listing :constructor constructor)
            payload (second payloads)
            resource (<! (rest/create! listing* payload))
            resource-url (string/upper-case (item-url 2))
            expected (rest/resource resource-url
                       :constructor constructor
                       :ok? true
                       :data {:url resource-url
                              :a "B"})]
        (is (= resource expected)))
      (done))))

(deftest listing-read-construction
  (async done
    (go
      (let [listing* (assoc listing :constructor constructor)
            resources (<! (rest/read listing*))
            urls [(item-url 1) (item-url 2)]
            expected (map-indexed
                       (fn [i payload]
                         (let [url (string/upper-case (nth urls i))
                               data (constructor (assoc payload :url url))]
                           (rest/resource url
                             :constructor constructor
                             :data data
                             :ok? true)))
                       payloads)]
        (is (= (:data resources) expected))
        (done)))))

(deftest listing-multiple-read-construction
  (async done
    (go
      (let [listing* (assoc listing :constructor constructor)
            resources (<! (rest/read listing*))
            second-read (<! (rest/read resources))
            urls [(item-url 1) (item-url 2)]
            expected (map-indexed
                       (fn [i payload]
                         (let [url (string/upper-case (nth urls i))
                               data (constructor (assoc payload :url url))]
                           (rest/resource url
                             :constructor constructor
                             :data data
                             :ok? true)))
                       payloads)]
        (is (= (:data second-read) expected))
        (done)))))

(deftest instance-read
  (async done
    (go
      (let [url (item-url 1)
            resource (rest/resource url)
            instance (<! (rest/read resource))
            expected (rest/resource url
                       :ok? true
                       :data (assoc (first payloads) :url url))]
        (is (= instance expected))
        (done)))))

(deftest instance-construction
  (async done
    (go
      (let [url (item-url 2)
            upper-url (string/upper-case url)
            resource (rest/resource url :constructor constructor)
            instance (<! (rest/read resource))
            expected (rest/resource upper-url
                       :constructor constructor
                       :ok? true
                       :data {:url upper-url
                              :a "B"})]
        (is (= instance expected))
        (done)))))

(deftest instance-error
  (async done
    (go
      (let [url (item-url 3)
            resource (rest/resource url)
            instance (<! (rest/read resource))]
        (is (= false (:ok? instance)))
        (is (= 404 (get-in instance [:data :status])))
        (done)))))

(deftest instance-error-retains-url
  (async done
    (go
      (let [url (item-url 3)
            resource (rest/resource url)
            instance (<! (rest/read resource))]
        (is (= url (:url instance)))
        (done)))))

(deftest instance-update
  (async done
    (go
      (let [url (item-url 1)
            resource (rest/resource url)
            payload {:c "d"}
            updated (<! (rest/update! resource payload))
            expected (rest/resource url
                       :ok? true
                       :data (assoc payload :url url))]
        (is (= updated expected))
        (done)))))

;; PhantomJS does not send the request body for PATCH:
;; https://github.com/ariya/phantomjs/issues/11384
; (deftest instance-patch
;   (async done
;     (go
;       (let [url (item-url 1)
;             resource (rest/resource url)
;             existing (<! (rest/read resource))
;             payload {:a "c"}
;             patched (<! (rest/patch! resource payload))
;             expected (rest/resource url
;                        :ok? true
;                        :data (merge (:data existing) payload {:url url}))]
;         (is (= patched expected))
;         (done)))))

(deftest instance-delete
  (async done
    (go
      (let [url (item-url 1)
            resource (rest/resource url)
            _ (<! (rest/delete! resource))
            lookup (<! (rest/read resource))]
        (is (= false (:ok? lookup)))
        (is (= 410 (get-in lookup [:data :status])))
        (done)))))

(deftest form-data
  (let [form-data (js/FormData.)
        opts {:params form-data}
        actual (rest/request-options :anything opts)
        expected-format rest/multipart-format]
    (is (= expected-format (:format actual)))
    (is (= form-data (:body actual)))
    (is (false? (contains? actual :params)))))

(deftest error-handler
  (async done
    (go
      (let [error-state (atom nil)
            listing (rest/resource-listing "http://localhost:4000/does-not-exist/"
                      :opts {:error-handler #(reset! error-state %)})
            resources (<! (rest/read listing))]
        (is (= 404 (:status @error-state)))
        (done)))))

(deftest default-error-handler
  (async done
    (go
      (let [error-state (atom nil)
            listing (rest/resource-listing "http://localhost:4000/does-not-exist/")]
        (binding [rest/*opts* (assoc rest/*opts* :error-handler #(reset! error-state %))]
          (let [resources (<! (rest/read listing))]
            (is (= 404 (:status @error-state)))
            (done)))))))

;; Async threading

(deftest async-threading
  (async done
    (go
      (let [payload {}
            data (<! (async->
                       listing
                       rest/read
                       :data
                       last
                       (rest/update! payload)
                       :data))
            expected {:url (item-url 2)}]
        (is (= expected data))
        (done)))))

(def e (ex-info "welp" {}))
(def returns-throwable (constantly e))

(deftest async-error-short-circuit
  (async done
    (go
      (let [call-count (atom 0)
            should-not-be-called (fn [_] (swap! call-count inc))
            data (try
                   (<! (async->
                         :whatever
                         returns-throwable
                         should-not-be-called))
                   (catch :default _ nil))]
        (is (= data e))
        (is (= 0 @call-count))
        (done)))))

(deftest async-nested-error-short-circuit
  (async done
    (go
      (let [call-count (atom 0)
            should-not-be-called (fn [_] (swap! call-count inc))
            nested (fn [_] (async->
                             :nested
                             returns-throwable))
            data (try
                   (<! (async->
                         :whatever
                         nested
                         should-not-be-called))
                   (catch :default _ nil))]
        (is (= data e))
        (is (= 0 @call-count))
        (done)))))
