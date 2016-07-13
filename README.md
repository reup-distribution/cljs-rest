# cljs-rest: A ClojureScript REST client

`[cljs-rest "1.0.0"]`

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
  ;; #cljs-rest.core.ResourceOptions{:success true :data {...}}

  (<! (rest/get entries))
  ;; #cljs-rest.core.ResourceListing{:success true :resources [] ,,,}

  (<! (rest/get entry-1))
  ;; #cljs-rest.core.Resource{:success false :status 404 ,,,}

  (<! (rest/post! entries {:title "Foo" :body "Lorem"}))
  ;; #cljs-rest.core.Resource{:success true :data {:url "https://api.whatever.org/entries/1/" :title "Foo"} ...}

  (<! (rest/first-resource entries))
  ;; #cljs-rest.core.Resource{:success true :data {...}}

  ;; And so on...

  (<! (rest/head entries))
  (<! (rest/head entry-1))
  (<! (rest/put! entry-1 {:title "Bar" :body "Ipsum"}))
  (<! (rest/patch! entry-1 {:body "Consectetur"}))
  (<! (rest/delete! entry-1)))
```

### Link headers

If your REST server returns `Link` headers for pagination, those headers are automatically parsed:

```clojure
(go
  (let [resources (<! (rest/get entries {:per_page 10 :page 3}))]
    (get-in resources [:headers :link])
    ;; {:prev {:url "https://api.whatever.org/entries/?per_page=10&page=2"
    ;;         :params {:per_page "10" :page "2"}}
    ;;  :next {:url "https://api.whatever.org/entries/?per_page=10&page=4"
    ;;         :params {:per_page "10" :page "4"}}}
    ))
```

### Ajax Options

Options are accepted from an `:opts` keyword argument. Refer to [cljs-http](https://github.com/r0man/cljs-http) for options documentation.

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
  rest/get
  :resources
  first
  (rest/put! {:foo "bar"}))
```

If any step in the async sequence returns an instance of `Error`, that will be the final value and subsequent steps will not  be called. Example:

```clojure
(defn response-error [listing]
  (if (:success listing)
      listing
      (ex-info "Request Error" listing)))

(async->
  some-listing
  rest/get
  response-error
  ;; If the request fails, the `ExceptionInfo` returned will be the final value,
  ;; and none of the following calls will be made:
  :resources
  first
  whatever)
```

### Releases

- 1.1.0 - `:link` header is parsed
- 1.0.0 - Full rewrite, see the [migration guide](docs/migration_guide_1.0.0.md)
    - Each resource type now populates the following response data:
        - `success`
        - `status`
        - `headers`
        - `body`
    - Switched to [cljs-http](https://github.com/r0man/cljs-http), with some conveniences built around it:
        - `headers` includes both string keys (cljs-http default) as well as keywords
        - Convenience function to configure default format with `configure-format!`
    - Response errors are now pushed to a configurable channel, rather than a callback
    - Request formatting is extensible. EDN, Transit, JSON, FormData and Multipart are provided.
- 0.1.5 - `ResourceListing` provides a `first-item` convenience method
- 0.1.4
    - Fixed an issue where requests for `Resource` instances would drop `:url`
    - Fixed an issue where reading multiple times from a `ResourceListing` with a `:constructor` specified would corrupt results
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
