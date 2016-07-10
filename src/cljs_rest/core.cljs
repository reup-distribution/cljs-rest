(ns cljs-rest.core
  (:refer-clojure :exclude [read])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [<! chan put!]]
            [cljs.core.async.impl.protocols :refer [ReadPort]]
            [cljs-http.client :as http]))

;; Async helpers

(defn ensure-channel [x]
  (if (satisfies? ReadPort x)
      x
      (go x)))

;; HTTP

(defn keys->keyword [m]
  (reduce
    (fn [acc [k v]]
      (assoc acc (keyword k) v))
    {}
    m))

(def config
  (atom
    {:collection-format :json-params
     :request-defaults {:method :get
                        :accept "application/json"}}))

(defn put-error! [opts error]
  (when-let [error-chan (or (:error-chan opts) (:error-chan @config))]
    (put! error-chan error)))

(defn request-options [url opts]
  (let [{:keys [collection-format request-defaults]} @config
        opts* (merge request-defaults {:url url} opts)
        {:keys [method params]} opts*
        params-key (cond
                     (and params (= :get method)) :query-params
                     (coll? params) collection-format)]
    (-> opts*
        (assoc params-key params)
        (dissoc nil :params))))

(defn request
  ([url] (request url {}))
  ([url opts]
    (go
      (let [opts* (request-options url opts)
            response (<! (http/request opts*))]
        (when-not (:success response)
          (put-error! opts response))
        (update response :headers keys->keyword)))))

(defn request->record [constructor url opts]
  (go
    (let [response (<! (request url opts))]
      (constructor (assoc response :url url :opts opts)))))

;; REST semantics

(defprotocol Restful
  (head [_])
  (options [_])
  (create! [_ data])
  (read [_] [_ params])
  (update! [_ data])
  (patch! [_ data])
  (delete! [_]))

;; Convenience constructor for record types in cljs-rest

(defn construct-record [constructor url & {opts :opts :or {opts {}} :as kwargs}]
  (constructor (assoc kwargs :opts opts :url url)))

;; HEAD resource representation

(declare map->ResourceHead)

(defrecord ResourceHead [url opts status success headers body]
  Restful
  (head [_]
    (request->record map->ResourceHead url (assoc opts :method :head))))

(def resource-head
  (partial construct-record map->ResourceHead))

;; OPTIONS resource representation

(declare map->ResourceOptions)

(defrecord ResourceOptions [url opts status success headers body]
  Restful
  (options [_]
    (request->record map->ResourceOptions url (assoc opts :method :options))))

(def resource-options
  (partial construct-record map->ResourceOptions))

;; Instance

(declare map->Resource)

(defn construct-resource [resource]
  (if (:success resource)
      (let [constructor (or (:constructor resource) identity)
            data (constructor (:body resource))]
        (assoc resource
          :url (:url data)
          :data data))
      (assoc resource :data nil)))

(defn construct-resource-response [resource response]
  (construct-resource
    (map->Resource (merge resource response))))

(defn request-resource [resource opts]
  (let [constructor (partial construct-resource-response resource)
        url (:url resource)]
    (request->record constructor url opts)))

(defrecord Resource [url opts constructor status success headers body data]
  Restful
  (head [_]
    (head (resource-head url :opts opts)))

  (options [this]
    (options (resource-options url :opts opts)))

  (read [this]
    (read this {}))

  (read [this params]
    (let [opts* (assoc opts :query-params params)]
      (request-resource this opts*)))

  (update! [this data]
    (let [opts* (assoc opts :method :put :params data)]
      (request-resource this opts*)))

  (patch! [this data]
    (let [opts* (assoc opts :method :patch :params data)]
      (request-resource this opts*)))

  (delete! [_]
    (request url
      (assoc opts
        :method :delete))))

(def resource
  (partial construct-record map->Resource))

;; Listing

(defprotocol RestfulListing
  (first-resource [_] [_ params]))

(declare map->ResourceListing)

(defn construct-listing [resource-listing]
  (if (:success resource-listing)
      (let [{:keys [opts item-opts constructor]} resource-listing
            constructor* (or constructor identity)
            opts* (or item-opts opts)
            base-resource (map->Resource {:success true :opts opts* :constructor constructor*})
            resources (map #(assoc base-resource :body %) (:body resource-listing))]
        (assoc resource-listing :resources (map construct-resource resources)))
      (assoc resource-listing :resources nil)))

(defn construct-listing-resources [resource-listing response]
  (construct-listing
    (map->ResourceListing (merge resource-listing response))))

(defn request-resources [resource-listing opts]
  (let [url (:url resource-listing)
        constructor (partial construct-listing-resources resource-listing)]
    (request->record constructor url opts)))

(defrecord ResourceListing [url opts item-opts constructor status success headers body resources]
  RestfulListing
  (first-resource [this] (first-resource this nil))

  (first-resource [this params]
    (go
      (let [result (<! (read this params))
            {:keys [success resources]} result
            no-items? (empty? resources)]
        (cond
          (not success)
          resources

          no-items?
          (do
            (let [resource (resource url
                             :opts opts
                             :constructor constructor
                             :success false
                             :status 404
                             :data nil)]
              (put-error! opts resource)
              resource))

          :else
          (first resources)))))

  Restful
  (head [_]
    (head (resource-head url :opts opts)))

  (options [this]
    (options (resource-options url :opts opts)))

  (create! [this data]
    (let [opts* (assoc opts :method :post :params data)]
      (request-resource this opts*)))

  (read [this]
    (read this {}))

  (read [this params]
    (let [opts* (assoc opts :query-params params)]
      (request-resources this opts*))))

(def resource-listing
  (partial construct-record map->ResourceListing))
