# cljs-rest: A ClojureScript REST client

`[cljs-rest "0.1.0"]`

A ClojureScript REST client, suitable for AJAX interaction with RESTful APIs.

### Basic usage

```clojure
(ns my-ns
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [cljs-rest :as rest]))

(def entries
  (rest/resource-listing "https://api.whatever.org/entries/"))

(def entry-1
  (rest/resource "https://api.whatever.org/entries/1/"))

(go
  (<! (rest/options entries))
  ;; #cljs-rest.core.ResourceOptions{:ok? true :data {...}}

  (<! (rest/read entries))
  ;; #cljs-rest.core.ResourceListing{:ok? true :data [] ,,,}

  (<! (rest/read entry-1))
  ;; #cljs-rest.core.Resource{:ok? false :data {:status 404 ...} ,,,}

  (<! (rest/create entries {:title "Foo" :body "Lorem"}))
  ;; #cljs-rest.core.Resource{:ok? true :data {:url "https://api.whatever.org/entries/1/" :title "Foo"} ...}

  ;; And so on...

  (<! (rest/head entries))
  (<! (rest/head entry-1))
  (<! (rest/update entry-1 {:title "Bar" :body "Ipsum"}))
  (<! (rest/patch entry-1 {:body "Consectetur"}))
  (<! (rest/delete entry-1)))
```

### Ajax Options

Options are accepted from an `:opts` keword argument. Refer to [cljs-ajax](https://github.com/JulianBirch/cljs-ajax) for options documentation.

Usage:

```clojure
(rest/resource-listing url :opts opts)
(rest/resource url :opts opts)
```

### Constructor

When provided, a `:constructor` keyword argument will be used to construct `Resource` data.

Usage:

```clojure
(defrecord Foo [])
(rest/resource-listing url :constructor map->Foo)
(rest/resource url :constructor map->Foo)
```

### Sequential async: `async->`

The `async->` macro, for conveniently performing sequential async calls, is similar to `clojure.core/->`, but the return value is a core async channel, and each step may return a channel as well.

Example usage:

```clojure
(async->
  some-listing
  rest/read
  :data
  first
  (assoc :foo "bar")
  rest/update!)
```
