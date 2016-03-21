(defproject cljs-rest "0.1.0"
  :description "A ClojureScript REST client"
  :url "https://github.com/reup-distribution/cljs-rest"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [cljs-ajax "0.5.3"]]
  :aliases {"test" ["run" "-m" "cljs-rest.test"]
            "cljsbuild" ["with-profile" "dev" "cljsbuild"]}
  :profiles {:dev {:dependencies [[cheshire "5.5.0"]
                                  [compojure "1.5.0"]
                                  [jumblerg/ring.middleware.cors "1.0.1"]
                                  [liberator "0.13"]
                                  [ring "1.4.0"]]
                   :plugins [[lein-cljsbuild "1.1.2"]]
                   :clean-targets ^{:protect false} [:target-path "target/cljsbuild"]
                   :cljsbuild {:builds [{:id "whitespace"
                                         :source-paths ["src" "test"]
                                         :compiler {:output-to "target/cljsbuild/build.js"
                                                    :output-dir "target/cljsbuild"
                                                    :optimizations :whitespace
                                                    :source-map "target/cljsbuild/build.js.map"}}]
                               :test-commands {"whitespace" ["phantomjs"
                                                             "test/phantomjs_runner.js"
                                                             "target/cljsbuild/build.js"]}}}})
