(defproject cljs-rest "1.1.0"
  :license {:name "BSD 2-clause \"Simplified\" License"
            :url "http://opensource.org/licenses/BSD-2-Clause"
            :year 2016
            :key "bsd-2-clause"}
  :description "A ClojureScript REST client"
  :url "https://github.com/reup-distribution/cljs-rest"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.89" :scope "provided"]
                 [cljs-http "0.1.41"]]
  :aliases {"test" ["cljsbuild" "test"]
            "cljsbuild" ["with-profile" "dev" "cljsbuild"]}
  :profiles {:dev {:jvm-opts ^:replace ["-Xmx2048m" "-server"]
                   :dependencies [[cheshire "5.5.0"]
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
                                                    :source-map "target/cljsbuild/build.js.map"}
                                         :notify-command ["phantomjs"
                                                          "test/phantomjs_runner.js"
                                                          "target/cljsbuild/build.js"]}]
                               :test-commands {"whitespace" ["phantomjs"
                                                             "test/phantomjs_runner.js"
                                                             "target/cljsbuild/build.js"]}}}})
