(ns cljs-rest.core
  (:refer-clojure :exclude [read])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [chan close! put! <!]]
            [cljs.core.async.impl.protocols :refer [ReadPort]]
            [ajax.core :as ajax]))

;; Async helpers

(defn ensure-channel [x]
  (if (satisfies? ReadPort x)
      x
      (go x)))

;; Serialization

(defn pr-strs [& args]
  (->> args
       (map pr-str)
       (string/join ",")))

;; HTTP

(def ^:dynamic *opts*
  {:method :get
   :format (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})})

(def multipart-format
  {:content-type "multipart/form-data"
   :write identity})

(defn request-options [url opts]
  (let [params (:params opts)
        form-data? (instance? js/FormData params)
        form-opts (when form-data?
                    (-> opts
                        (assoc :format multipart-format :body params)
                        (dissoc :params)))]
    (merge *opts* (or form-opts opts) {:uri url})))

(defn request [url opts]
  (let [chan (chan)
        {:keys [error-handler process]
         :or {error-handler identity process identity}} opts]
    (ajax/ajax-request
      (assoc (request-options url opts)
        :handler (fn [[ok? data :as x]]
                   (when-not ok?
                     (error-handler data))
                   (let [processed (process x)]
                     (put! chan processed)
                     (close! chan)))))
    chan))

;; REST semantics

(defprotocol Restful
  (head [_])
  (options [_])
  (create! [_ data])
  (read [_] [_ params])
  (update! [_ data])
  (patch! [_ data])
  (delete! [_]))

;; HEAD resource representation
(defrecord ResourceHead [url opts ok? data]
  Restful
  (head [_]
    (request url
      (assoc opts
        :method :head
        :process (fn [[ok? data]]
                   (ResourceHead. url opts ok? data))
        :response-format {:read (fn [xhr]
                                  (reduce
                                    (fn [acc [k v]]
                                      (let [kw (keyword (string/lower-case k))]
                                        (assoc acc kw v)))
                                    {}
                                    (js->clj (.getResponseHeaders xhr))))
                          :description "raw"
                          :content-type "*"
                          :type :document}))))

(defn resource-head [url & {:keys [opts ok? data] :or {opts {}}}]
  (ResourceHead. url opts ok? data))

;; OPTIONS resource representation

(defrecord ResourceOptions [url opts ok? data]
  Restful
  (options [_]
    (request url
      (assoc opts
        :method :options
        :process (fn [[ok? data]]
                   (ResourceOptions. url opts ok? data))))))

(defn resource-options [url & {:keys [opts ok? data] :or {opts {}}}]
  (ResourceOptions. url opts ok? data))

;; Instance

(defprotocol RestfulInstance
  (instance-constructor [_]))

(defrecord Resource [url opts constructor ok? data]
  RestfulInstance
  (instance-constructor [_]
    (fn [[ok? item]]
      (let [constructed (constructor item)
            url (:url constructed)]
        (Resource. url opts constructor ok? constructed))))

  Restful
  (head [_]
    (head (resource-head url :opts opts)))

  (options [this]
    (options (resource-options url :opts opts)))

  (read [this]
    (read this nil))

  (read [this params]
    (request url
      (assoc opts
        :params params
        :process (instance-constructor this))))

  (update! [this changes]
    (request url
      (assoc opts
        :method :put
        :params changes
        :process (instance-constructor this))))

  (patch! [this changes]
    (request url
      (assoc opts
        :method :patch
        :params changes
        :process (instance-constructor this))))

  (delete! [_]
    (request url
      (assoc opts
        :method :delete))))

(defn resource [url & {:keys [opts constructor ok? data] :or {opts {} constructor identity}}]
  (Resource. url opts constructor ok? data))

;; Listing

(defprotocol RestfulListing
  (item-constructor [_])
  (items-constructor [_]))

(defrecord ResourceListing [url opts item-opts constructor ok? data]
  RestfulListing
  (item-constructor [_]
    (fn [[ok? item]]
      (let [constructed (constructor item)
            url (:url constructed)]
        (Resource. url (or item-opts opts) constructor ok? constructed))))

  (items-constructor [this]
    (fn [[ok? data]]
      (if ok?
          (let [constructor (item-constructor this)
                constructed (map #(constructor [ok? %]) data)]
            (ResourceListing. url opts item-opts constructor ok? constructed))
          (ResourceListing. url opts item-opts constructor ok? data))))

  Restful
  (head [_]
    (head (resource-head url :opts opts)))

  (options [this]
    (options (resource-options url :opts opts)))

  (create! [this data]
    (request url
      (assoc (or item-opts opts)
        :method :post
        :params data
        :process (item-constructor this))))

  (read [this]
    (read this nil))

  (read [this params]
    (request url
      (assoc opts
        :params params
        :process (items-constructor this)))))

(defn resource-listing
  [url & {:keys [opts item-opts constructor ok? data] :or {opts {} constructor identity}}]
  (ResourceListing. url opts item-opts constructor ok? data))
