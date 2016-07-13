(ns cljs-rest.mock-server
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs-http.client :as http]
            [cljs-http.util :refer [json-encode json-decode]]))

(def routes
  {#"^/entries/$" :entries
   #"^/entries/(.*?)/$" :entry})

(defmulti request
  "Dispatches requests to handlers according to request method and route"
  (fn [opts]
    (let [{:keys [request-method uri] :or {request-method :get}} opts
          routes* (seq routes)]
      (loop [[pattern handler] (first routes*)
             next-routes (next routes*)]
        (cond
          (re-find pattern uri) [request-method handler]
          next-routes (recur (first next-routes) (next next-routes)))))))

;; Now let's do some gnarly stuff. It's not really safe to use with-redefs
;; around async code, so we pretend by setting, then restoring the request
;; function in cljs-http. This also plays nicely with cljs.test/use-fixtures.
(def wrapped-request
  (http/wrap-request request))

(def http-request http/request)

(defn mock-request! []
  (set! http/request wrapped-request))

(defn restore-request! []
  (set! http/request http-request))

;; Responses

(def default-response
  {:status 200
   :success true})

(def default-headers
  {"content-type" "application/json"})

(defn with-default-headers [m]
  (let [headers (merge default-headers (:headers m))]
    (assoc m :headers headers)))

(defn with-json-body [m]
  (let [body (:body m)]
    (if (coll? body)
        (update m :body json-encode)
        m)))

(defn response
  ([] (response {}))
  ([m]
    (go
      (->> m
           (merge default-response)
           with-default-headers
           with-json-body))))

(defn not-found []
  (response {:status 404 :success false}))

;; Mock API state

(def entries
  (atom []))

(defn create-entry! [entry]
  (let [id (inc (count @entries))
        entry* (assoc entry :url (str "/entries/" id "/"))]
    (swap! entries conj entry*)
    entry*))

;;;; Request handlers

;; Entries

(defmethod request [:head :entries] [_]
  (response {:headers {"content-length" "0"}}))

(defmethod request [:options :entries] [_]
  (response {:body {:name "Entries"}}))

(defn pagination-params [req]
  (let [params (http/parse-query-params (:query-string req))
        per-page (int (:per-page params 10))
        page (int (:page params 1))
        skip (* per-page (dec page))]
    {:per-page per-page
     :page page
     :skip skip}))

(defn paginate [params items]
  (let [{:keys [per-page page skip]} params]
    (->> items
         (drop skip)
         (take per-page))))

(defn pagination-url [base-url per-page page]
  (str base-url "?" (http/generate-query-string {:per-page per-page :page page})))

(defn link-header [links]
  (string/join ", "
    (reduce
      (fn [acc [k url]]
        (when url
          (conj acc (str "<" url ">; rel=\"" (name k) "\""))))
      []
      links)))

(defn pagination-headers [base-url params items]
  (let [{:keys [per-page page skip]} params
        total (count items)
        prev (when (> skip 0)
               (pagination-url base-url per-page (dec page)))
        next (when (> total (+ skip per-page))
               (pagination-url base-url per-page (inc page)))
        link-header (link-header {:prev prev :next next})]
    (when link-header
      {"link" link-header})))

(defmethod request [:get :entries] [req]
  (let [params (pagination-params req)
        items @entries]
    (response
      {:headers (pagination-headers (:uri req) params items)
       :body (paginate params items)})))

(defmethod request [:post :entries] [req]
  (let [data (json-decode (:body req))
        entry (create-entry! data)]
    (response {:body entry})))

;; Entry

(defn replace-entry! [idx data]
  (when-let [entry (nth @entries idx)]
    (let [replacement (assoc data :url (:url entry))]
      (swap! entries assoc idx replacement)
      replacement)))

(defn update-entry! [idx data]
  (when-let [entry (nth @entries idx)]
    (let [update (merge entry data)]
      (swap! entries assoc idx update)
      update)))

(def url-entry-index
  (atom {}))

(add-watch entries :url-entry-index
  (fn [_ _ _ entries]
    (swap! url-entry-index merge
      (into {}
        (map-indexed
          (fn [idx entry]
            [(:url entry) idx])
          entries)))))

(defn find-entry-index [req]
  (let [url (:uri req)]
    (get @url-entry-index url)))

(defmethod request [:get :entry] [req]
  (if-let [idx (find-entry-index req)]
    (let [entry (nth @entries idx)]
      (if (nil? entry)
          (response
            {:status 410
             :success false})
          (response {:body entry})))
    (not-found)))

(defmethod request [:put :entry] [req]
  (if-let [idx (find-entry-index req)]
    (let [data (json-decode (:body req))
          replaced (replace-entry! idx data)]
      (response {:body replaced}))
    (not-found)))

(defmethod request [:patch :entry] [req]
  (if-let [idx (find-entry-index req)]
    (let [data (json-decode (:body req))
          replaced (update-entry! idx data)]
      (response {:body replaced}))
    (not-found)))

(defmethod request [:delete :entry] [req]
  (if-let [idx (find-entry-index req)]
    (do
      (swap! entries assoc idx nil)
      (response {:status 204}))
    (not-found)))

;; Fallback

(defmethod request :default [_]
  (not-found))
