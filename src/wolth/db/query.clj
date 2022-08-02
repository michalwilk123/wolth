(ns wolth.db.query
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [clojure.string :as str]
            [honey.sql.helpers :as hsql]))

(defn create-pairs
  [coll]
  (assert (= (mod (count coll) 2) 0) "Number of arguments must be even!")
  (partition 2 2 coll))

(comment
  (create-pairs (range 10))
  (create-pairs (range 11)))
; Available operations:
; - sort "<<" ">>"
; - filter - znaki: "==" "<>" ">" ">=" "LEN"
; - * (all)
; - detail - zwykły znak "="

;; TODO: CHANGE THIS!!!
(defn can-join? [pair] true)

(defn check-if-joining-valid
  "Check if this collection of objects can be joined together (checking if foreign relationship exists)"
  [query-pairs]
  (let [joining-valid (->> query-pairs
                           (map first)
                           (partition 2 1)
                           (every? can-join?))]
    (if joining-valid
      query-pairs
      (throw (AssertionError. (str "Cannot perform join on those objects"
                                   (map first query-pairs)))))))

(comment
  (check-if-joining-valid
    '(("person" "id=1") ("comment" "*") ("movie" "min-age>12"))))

(def objects-url-spec (atom {}))

(defn build-subquery [table-name selector] "DSKNDKJSNKJDN")

(comment
  (build-subquery "person" "id=111"))

(defn merge-sql-subqueries [subqueries] "MERGED")

(defn build-query-from-url
  [url-string]
  (swap! objects-url-spec assoc :person [[:id] [:name :string] [:age :int]])
  (->> url-string
       (#(str/split % #"/"))
       (remove str/blank?)
       (create-pairs)
       (check-if-joining-valid)
       (map (partial apply build-subquery))
       (merge-sql-subqueries)))


(comment
  (build-query-from-url "/person/id=111/"))