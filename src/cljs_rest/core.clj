(ns cljs-rest.core)

(defn async-form->fn [form]
  (let [x (gensym "x")]
    (list 'fn [x]
      (if (seq? form)
          `(~(first form) ~x ~@(next form))
          (list form x)))))

;; `async->` is essentially the behavior of `clojure.core/->` married to
;; `cljs.core.async.macros/go-loop`, whch itself is essentially `(go (loop ...))`.
(defmacro async->
  "Like clojure.core/-> except it returns a channel, and each step
  may be a channel as well."
  [x & forms]
  (let [x* (list 'fn '[_] (if (seq? x) `(do ~x) x))
        form-fns (map async-form->fn forms)]
    `(cljs.core.async.macros/go-loop
       [f# ~x*
        fns# (list ~@form-fns)
        prev-result# nil]
       (let [chan# (ensure-channel (f# prev-result#))
             result# (cljs.core.async/<! chan#)
             error?# (instance? js/Error result#)]
         (if (and (not error?#) fns#)
             (recur (first fns#) (next fns#) result#)
             result#)))))
