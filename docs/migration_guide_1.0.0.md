# Migration guide for cljs-rest 1.0.0

### Configuration

An obvious use case that was poorly handled in older versions is to set-and-forget configuration. As such, configuration is now stored as an `atom` rather than a dynamic `var`.

0.1.5                                    | 1.0.0
-----                                    | -----
`(binding [rest/*opts* ...] ...)`        | N/A <sup>[[Dynamic binding](#dynamic-binding)]</sup>
`(set! rest/*opts* ...)`                 | `(swap! rest/config assoc :request-defaults ...)` <sup>[[Request options](#request-options)]</sup>
`:response-format ...`                   | `(rest/configure-format! ...)` (`:json`, `:edn` and `:transit` are supported)

### Dynamic binding

It turns out `binding` [does not behave as expected](http://dev.clojure.org/jira/browse/CLJS-1634) with asynchronous ClojureScript. The best approximate replacement is to include `:opts` keyword argument when constructing a resource type.


### Request options

The underlying request library was changed to [cljs-http](https://github.com/r0man/cljs-http) from [cljs-ajax](https://github.com/JulianBirch/cljs-ajax).


### Multipart

0.1.5                                    | 1.0.0
-----                                    | -----
`(rest/post listing (FormData. ...))`    | `(rest/post listing (rest/multipart-params {:field val}))`

### Error handling

0.1.5                                    | 1.0.0
-----                                    | -----
`:ok?`                                   | `:success`
`(get-in resource [:data :status])` etc  | `(:status resource)`
`:error-handler (fn [err] ...)`          | `:error-chan (chan)`

### ResourceListing

0.1.5                                    | 1.0.0
-----                                    | -----
`(:data listing)`                        | `(:resources listing)`
`(first-item listing)`                   | `(first-resource listing)`
