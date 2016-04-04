# cljs-rest: A ClojureScript REST client

`[cljs-rest "0.1.3"]`

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

If any step in the async sequence returns an instance of `Error`, that will be the final value and subsequent steps will not  be called. Example:

```clojure
(defn response-error [listing]
  (if (:ok? listing)
      listing
      (ex-info "Request Error" listing)))

(async->
  some-listing
  rest/read
  response-error
  ;; If the request fails, the `ExceptionInfo` returned will be the final value,
  ;; and none of the following calls will be made:
  :data
  first
  whatever)
```

### Releases

- 0.1.3 - Any step in `async->` returning an instance of `Error` will short-circuit the chain
- 0.1.2 - Bugfix: when present, `:error-handler` in `cljs-rest.core/*opts*` will be called for error responses
- 0.1.1 - `FormData` payloads are automatically sent as `multipart/form-data`
- 0.1.0 - initial release

### License

Copyright (c) 2016, ReUp Distribution Inc
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
