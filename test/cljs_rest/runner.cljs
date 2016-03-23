(ns cljs-rest.runner
  (:require [cljs.test :refer-macros [run-all-tests]]
            [cljs-rest.core-test]))

(enable-console-print!)

(def complete-state (atom nil))

(defn ^:export complete []
  @complete-state)

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (reset! complete-state m))

(defn ^:export run []
  (run-all-tests #"cljs-rest.*-test"))

