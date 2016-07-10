(ns cljs-rest.core-test
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cljs-rest.core :refer [async->]])
  (:require [clojure.string :as string]
            [cljs.test :refer-macros [async deftest is use-fixtures]]
            [cljs.core.async :refer [<! timeout]]
            [cljs-rest.core :as rest]))

(def default-config @rest/config)

(use-fixtures :each
  {:before (fn [] (rest/set-config-format! :json))
   :after (fn [] (reset! rest/config default-config))})

(defn configure-error-chan! []
  (let [error-chan (timeout 1000)]
    (swap! rest/config assoc :error-chan error-chan)
    error-chan))

(def listing-url "http://0.0.0.0:4000/entries/")

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
        (is (= "0" (get-in head [:headers :content-length])))
        (done)))))

(deftest listing-options
  (async done
    (go
      (let [options (<! (rest/options listing))
            alt (<! (rest/options options))
            expected {:name "Entries"}]
        (is (= expected (:body options) (:body alt)))
        (done)))))

(deftest listing-create
  (async done
    (go
      (let [payload (first payloads)
            resource (<! (rest/create! listing payload))
            resource-url (item-url 1)
            expected (assoc payload :url resource-url)]
        (is (= expected (:data resource)))
        (is (= resource-url (:url resource)))
        (done)))))

(deftest listing-read
  (async done
    (go
      (let [resources (<! (rest/read listing))
            first-resource (first (:resources resources))
            expected (assoc (first payloads) :url (item-url 1))]
        (is (= expected (:data first-resource)))
        (is (= (:url expected) (:url first-resource)))
        (done)))))

(deftest listing-read-params
  (async done
    (go
      (let [resources (<! (rest/read listing {:empty "results"}))]
        (is (= (list) (:resources resources)))
        (done)))))

(deftest listing-error
  (async done
    (go
      (let [listing (rest/resource-listing "http://localhost:4000/does-not-exist/")
            resources (<! (rest/read listing))]
        (is (= false (:success resources)))
        (is (= 404 (:status resources)))
        (done)))))

(deftest listing-error-chan
  (async done
    (go
      (let [error-chan (configure-error-chan!)
            listing (rest/resource-listing "http://localhost:4000/does-not-exist/")
            resources (<! (rest/read listing))
            error (<! error-chan)]
        (is (= false (:success error)))
        (is (= 404 (:status error)))
        (done)))))

(deftest listing-first-resource
  (async done
    (go
      (let [resource (<! (rest/first-resource listing))
            expected (assoc (first payloads) :url (item-url 1))]
        (is (= expected (:data resource)))
        (done)))))

(deftest listing-first-resource-empty-error
  (async done
    (go
      (let [resource (<! (rest/first-resource listing {:empty "results"}))]
        (is (= false (:success resource)))
        (is (= 404 (:status resource)))
        (done)))))

(deftest listing-first-resource-empty-error-chan
  (async done
    (go
      (let [error-chan (configure-error-chan!)
            resources (<! (rest/first-resource listing {:empty "results"}))
            error (<! error-chan)]
        (is (= 404 (:status error)))
        (done)))))

(deftest listing-create-construction
  (async done
    (go
      (let [listing* (assoc listing :constructor constructor)
            payload (second payloads)
            resource (<! (rest/create! listing* payload))
            expected {:url (string/upper-case (item-url 2))
                      :a "B"}]
        (is (= expected (:data resource))))
      (done))))

(deftest listing-read-construction
  (async done
    (go
      (let [listing* (assoc listing :constructor constructor)
            resources (<! (rest/read listing*))
            urls [(item-url 1) (item-url 2)]
            expected (map-indexed
                       (fn [i payload]
                         (let [url (string/upper-case (nth urls i))]
                           (constructor (assoc payload :url url))))
                       payloads)
            data (map :data (:resources resources))]
        (is (= expected data))
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
                         (let [url (string/upper-case (nth urls i))]
                           (constructor (assoc payload :url url))))
                       payloads)
            data (map :data (:resources second-read))]
        (is (= expected data))
        (done)))))

(deftest instance-read
  (async done
    (go
      (let [url (item-url 1)
            resource (rest/resource url)
            instance (<! (rest/read resource))
            expected (assoc (first payloads) :url url)]
        (is (= expected (:data instance)))
        (done)))))

(deftest instance-construction
  (async done
    (go
      (let [url (item-url 2)
            resource (rest/resource url :constructor constructor)
            instance (<! (rest/read resource))
            expected {:url (string/upper-case url)
                      :a "B"}]
        (is (= expected (:data instance)))
        (done)))))

(deftest instance-error
  (async done
    (go
      (let [url (item-url 3)
            resource (rest/resource url)
            instance (<! (rest/read resource))]
        (is (= false (:success instance)))
        (is (= 404 (:status instance)))
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
            expected (assoc payload :url url)]
        (is (= expected (:data updated)))
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
;             expected (merge (:data existing) payload {:url url})]
;         (is (= expected (:data patched)))
;         (done)))))

(deftest instance-delete
  (async done
    (go
      (let [url (item-url 1)
            resource (rest/resource url)
            deletion (<! (rest/delete! resource))
            lookup (<! (rest/read resource))]
        (is (= true (:success deletion)))
        (is (= false (:success lookup)))
        (is (= 410 (:status lookup)))
        (done)))))

(deftest multipart-data
  (let [params {:a "b"}
        multipart (rest/multipart-params params)
        ordered-params [[:a "b"]]
        ordered (rest/ordered-multipart-params ordered-params)
        actual-multipart (rest/request-options :anything {:params multipart})
        actual-ordered (rest/request-options :anything {:params ordered})]
    (is (= (seq params) (:multipart-params actual-multipart)))
    (is (= ordered-params (:multipart-params actual-ordered)))
    (is (false? (contains? actual-multipart :params)))
    (is (false? (contains? actual-ordered :params)))))

(deftest per-request-error-chan
  (async done
    (go
      (let [error-chan (timeout 1000)
            listing (rest/resource-listing "http://localhost:4000/does-not-exist/"
                      :opts {:error-chan error-chan})
            resources (<! (rest/read listing))
            error (<! error-chan)]
        (is (= 404 (:status error)))
        (done)))))

;; Async threading

(deftest async-threading
  (async done
    (go
      (let [payload {}
            data (<! (async->
                       listing
                       rest/read
                       :resources
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
            data (<! (async->
                       :whatever
                       returns-throwable
                       should-not-be-called))]
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
            data (<! (async->
                       :whatever
                       nested
                       should-not-be-called))]
        (is (= data e))
        (is (= 0 @call-count))
        (done)))))
