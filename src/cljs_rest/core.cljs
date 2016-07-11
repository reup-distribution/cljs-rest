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

(def format-request-defaults
  {:edn      {:accept "application/edn"}
   :json     {:accept "application/json"}
   :transit  {:accept "application/transit+json"}})

(def format->params-key
  {:edn      :edn-params
   :json     :json-params
   :transit  :transit-params})

(def config
  (atom
    {:format nil
     :request-defaults nil}))

(defn configure-format [config format]
  (-> config
      (assoc :format format)
      (update :request-defaults merge (format-request-defaults format))))

(defn configure-format! [format]
  (swap! config #(configure-format % format)))

(defn request-defaults [x]
  (let [{:keys [format request-defaults]} @config]
    (if (coll? x)
        (merge request-defaults (format-request-defaults format))
        request-defaults)))

(defprotocol InfersRequestOptions
  "Provides extension semantics for generating request options for arbitrary
  types passed as body params. Example:

  (extend-protocol InfersRequestOptions
    MyTransitType
    (-opts [_] {:accept \"application/transit+json\"})
    (-params [this] {:transit-params this}))"

  (-opts [_] "Returns arbitrary request options for params type")
  (-params [_] "Returns a map of cljs-http params for request options"))

(extend-protocol InfersRequestOptions
  js/FormData
  (-params [form-data] {:body form-data})

  nil
  (-params [_] nil)

  default
  (-opts [x] (request-defaults x))
  (-params [x]
    ;; The conversion to JSON or similar serializations depends on `clj->js`,
    ;; which for our purposes is restricted to types satisfying ICollection.
    (let [k (if (coll? x)
                (format->params-key (:format @config) :params)
                :params)]
      {k x})))

(defrecord MultipartParams []
  InfersRequestOptions
  (-params [this] {:multipart-params (seq this)}))

(def multipart-params map->MultipartParams)

;; Seen in cljs-http issues, some APIs require multipart payloads to be sent in
;; a specific order.
(deftype OrderedMultipartParams [xs]
  InfersRequestOptions
  (-params [_] {:multipart-params xs}))

(defn ordered-multipart-params [xs]
  {:pre [(seqable? xs)]}
  (->OrderedMultipartParams xs))

(defn request-options [url opts]
  (let [params (:params opts)
        base-opts (-opts params)
        opts* (merge base-opts opts)
        params* (-params params)]
    (-> opts*
        (dissoc :params)
        (merge params*)
        (assoc :url url)
        (dissoc nil))))

(defn put-error! [opts error]
  (when-let [error-chan (or (:error-chan opts) (:error-chan @config))]
    (put! error-chan error)))

(defn with-keywords [m]
  (reduce
    (fn [acc [k v]]
      (assoc acc (keyword k) v))
    m
    m))

(defn request
  ([url] (request url {}))
  ([url opts]
    (go
      (let [opts* (request-options url opts)
            response (<! (http/request opts*))]
        (when-not (:success response)
          (put-error! opts response))
        (update response :headers with-keywords)))))

;; REST semantics

(defprotocol Initializable
  (-init [_]))

(defprotocol Restful
  (head [_])
  (options [_])
  (create! [_ data])
  (read [_] [_ params])
  (update! [_ data])
  (patch! [_ data])
  (delete! [_]))

(defn request-restful [constructor url opts]
  (go
    (let [response (<! (request url opts))]
      (-init (constructor (assoc response :url url :opts opts))))))

(defn- construct-record
  "Convenience constructor for resource record types defined by cljs-rest"
  [constructor url & {opts :opts :or {opts {}} :as kwargs}]
  (-init (constructor (assoc kwargs :opts opts :url url))))

;; HEAD resource representation

(declare map->ResourceHead)

(defrecord ResourceHead [url opts status success headers data]
  Initializable
  (-init [this] (assoc this :data headers))

  Restful
  (head [_]
    (request-restful map->ResourceHead url (assoc opts :method :head))))

(def resource-head
  (partial construct-record map->ResourceHead))

;; OPTIONS resource representation

(declare map->ResourceOptions)

(defrecord ResourceOptions [url opts status success headers body data]
  Initializable
  (-init [this] (assoc this :data body))

  Restful
  (options [_]
    (request-restful map->ResourceOptions url (assoc opts :method :options))))

(def resource-options
  (partial construct-record map->ResourceOptions))

;; Instance

(declare map->Resource)

(defn- request-resource [resource opts]
  (let [constructor #(map->Resource (merge resource %))
        url (:url resource)]
    (request-restful constructor url opts)))

(defrecord Resource [url opts constructor status success headers body data]
  Initializable
  (-init [this]
    (let [{:keys [url success constructor body]} this]
      (if success
          (let [constructor* (or constructor identity)
                data (constructor* body)]
            (assoc this
              :url (or (:url data) url)
              :data data))
          (assoc this :data nil))))

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

(defn- request-resources [resource-listing opts]
  (let [constructor #(map->ResourceListing (merge resource-listing %))
        url (:url resource-listing)]
    (request-restful constructor url opts)))

(defrecord ResourceListing [url opts item-opts constructor status success headers body resources]
  Initializable
  (-init [this]
    (if success
        (let [opts* (or item-opts opts)
              resource* #(resource nil :success true :opts opts* :constructor constructor :body %)]
          (assoc this :resources (map resource* body)))
        (assoc this :resources nil)))

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
          (let [resource (resource url
                           :opts opts
                           :constructor constructor
                           :success false
                           :status 404
                           :data nil)]
            (put-error! opts resource)
            resource)

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
