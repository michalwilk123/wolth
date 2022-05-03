#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns dummy-wolth-module)

; this is a test module showcasing loading external modules


(def test-inter
  {:name :testowy-interceptor,
   :enter (fn enter-func [ctx] (println "entering args") ctx),
   :leave (fn leave-func [ctx] (println "leaving args") ctx)})

(defn test-inter-w-args
  [& args]
  {:name :testowy-interceptor-z-argumentami,
   :enter (fn enter-func [ctx] (println "entering args interceptor:" args) ctx),
   :leave (fn leave-func [ctx] (println "leaving args interceptor:" args) ctx)})
