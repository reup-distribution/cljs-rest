(ns cljs-rest.test
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]
            [cheshire.generate :as generate]
            [compojure.core :refer [defroutes ANY]]
            [liberator.core :refer [defresource]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]])
  (:import [java.net URL])
  (:gen-class))

;;;; SERVER

(def ^:dynamic *port* 4000)

;; Mostly borrowed from http://clojure-liberator.github.io/liberator/tutorial/all-together.html

(generate/add-encoder java.net.URL generate/encode-str)

(defn body-as-string [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn parse-json [ctx key]
  (when (#{:put :post :patch} (get-in ctx [:request :request-method]))
    (try
      (if-let [body (body-as-string ctx)]
        (let [data (json/parse-string body)]
          [false {key data}])
        {:message "No body"})
      (catch Exception e
        (.printStackTrace e)
        {:message (format "IOException: %s" (.getMessage e))}))))

(defn content-type [ctx]
  (let [header (get-in ctx [:request :headers "content-type"])]
    (string/replace header #";\s*charset=.*" "")))

(defn check-content-type [ctx content-types]
  (if (#{:put :post} (get-in ctx [:request :request-method]))
    (or
     (some #{(content-type ctx)} content-types)
     [false {:message "Unsupported Content-Type"}])
    true))

(defonce entries (ref {}))
(defonce entry-count (atom 0))

(defn build-entry-url
  [request id]
  (URL. (format "%s://%s:%s%s%s/"
                (name (:scheme request))
                (:server-name request)
                (:server-port request)
                (:uri request)
                (str id))))

(defn list-entries [ctx]
  (if (empty? (get-in ctx [:request :query-params]))
      (->> entries deref vals (remove nil?) json/encode)
      "[]"))

(defn list-options [_]
  (json/encode {:name "Entries"}))

(defresource list-resource
  :available-media-types ["*" "application/json"]
  :allowed-methods [:get :head :options :post]
  :known-content-type? #(check-content-type % ["application/json"])
  :malformed? #(parse-json % ::data)
  :post! #(let [id (str (swap! entry-count inc))
                url (build-entry-url (:request %) id)
                data (assoc (::data %) :url url)]
            (dosync (alter entries assoc id data))
            {::id id})
  :post-redirect? true
  :location #(build-entry-url (get % :request) (get % ::id))
  :handle-ok list-entries
  :handle-options list-options)

(defn entry [ctx]
  (if (empty? (get-in ctx [:request :query-params]))
      (json/encode (::entry ctx))
      "{}"))

(defresource entry-resource [id]
  :allowed-methods [:delete :get :head :patch :put]
  :known-content-type? #(check-content-type % ["application/json"])
  :exists? (fn [_]
             (let [e (get @entries id)]
               (if-not (nil? e)
                 {::entry e})))
  :existed? (fn [_] (nil? (get @entries id ::sentinel)))
  :available-media-types ["*" "application/json"]
  :handle-ok (fn [_] (json/encode (get @entries id)))
  :delete! (fn [_] (dosync (alter entries assoc id nil)))
  :malformed? #(parse-json % ::data)
  :can-put-to-missing? false
  :put! #(dosync (alter entries assoc id (assoc (::data %) :url (:url (::entry %)))))
  :patch-content-types ["application/json"]
  :patch! #(dosync (alter entries update id merge (::data %)))
  :respond-with-entity? true
  :new? (fn [_] (nil? (get @entries id ::sentinel))))

(defroutes routes
  (ANY ["/entries/:id{[0-9]+}/"] [id] (entry-resource id))
  (ANY "/entries/" [] list-resource))

(def handler
  (-> routes
      wrap-params
      (wrap-cors #".*")))

;;;; CLIENT

(def ^:dynamic *stream-out* println)

(defn- print-stream
  [stream]
  (let [stream-seq (->> stream
                        (java.io.InputStreamReader.)
                        (java.io.BufferedReader.)
                        line-seq)]
    (do
      (doseq [line stream-seq]
        (*stream-out* line)))))

(defn exec-stream
  "Executes a shell command, streaming stdout and stderr to shell"
  [command & args]
  (let [runtime  (Runtime/getRuntime)
        proc     (.exec runtime (into-array (cons command args)))
        stdout   (.getInputStream proc)
        stderr   (.getErrorStream proc)
        _        (future (print-stream stdout))
        _        (future (print-stream stderr))
        proc-ret (.waitFor proc)]
      proc-ret))

(defn run-phantom-tests []
  (exec-stream "lein" "cljsbuild" "test"))

(defn -main [& _]
  (run-jetty handler {:port *port* :join? false})
  (System/exit (run-phantom-tests)))
