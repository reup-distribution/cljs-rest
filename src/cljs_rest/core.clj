(ns cljs-rest.core)

(defn async-form->fn [form]
  (let [x (gensym "x")]
    (list 'fn [x]
      (if (seq? form)
          `(~(first form) ~x ~@(next form))
          (list form x)))))

(defmacro async-> [x & forms]
  (let [x* (list 'fn '[_] (if (seq? x) `(do ~x) x))
        form-fns (map async-form->fn forms)]
    `(cljs.core.async.macros/go-loop
       [f# ~x*
        fns# (list ~@form-fns)
        prev-result# nil]
       (let [chan# (ensure-channel (f# prev-result#))
             result# (cljs.core.async/<! chan#)]
         (if fns#
             (recur (first fns#) (next fns#) result#)
             result#)))))
