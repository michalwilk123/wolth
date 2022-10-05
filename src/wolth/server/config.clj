(ns wolth.server.config
  (:require [next.jdbc :as jdbc]))

(defonce cursor-pool (atom nil))

(defonce app-data-container (atom nil))

(defonce routes (atom nil))

(defonce function-bank (atom nil))

(defonce _test_atom_1 (atom 1))
(defonce _test_atom_2 (atom 2))

(defn set-atom-state! [dict] (run! (fn [[key value]] (reset! key value)) dict))

(comment
  (set-atom-state! {_test_atom_1 121, _test_atom_2 333}))

#_"This macro mocks the environment atoms. Very useful for testing
   Be sure to add ' sign before the function call"
(defmacro def-context
  [name data]
  `(defn- ~name
     [expr#]
     (let [backed-up-data# (doall (map deref (keys ~data)))]
       (do
         (set-atom-state! ~data)
         (let [result#
                 (if (every? seq? expr#) (last (map eval expr#)) (eval expr#))]
           (set-atom-state! (zipmap (keys ~data) backed-up-data#))
           result#)))))

(comment
  (def-context test-context {_test_atom_1 {:aaa "EEEE"}})
  (test-context '((println @_test_atom_1) (println "DSAD") (+ 1 2 3)))
  (test-context '(println "ELO 320"))
  (test-context '(* 22 11))
  (reset! _test_atom_1 333)
  (println @_test_atom_1))

(comment
  (clojure.walk/macroexpand-all '(def-atom-context tt {:aaa "dsadsa"})))

