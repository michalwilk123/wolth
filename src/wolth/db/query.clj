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

; Available operations:
; - sort "<<" ">>"
; - filter - znaki: "==" "<>" ">" ">=" "LEN". 
;    Połączone za pomocą 
; - * (all)
; - detail - zwykły znak "="

;; (def)

(def detail-reg #"(?i)[a-z]+[0-9]*=[a-z0-9]+")
(def all-reg #"\*")
(def sort-reg #"((<<)|(>>)[a-zA-Z]+[0-9]*)+")
;; (def list-reg #"()")

(defn detail-lexer [val] 
  ( re-matches detail-reg val))

(comment
  (detail-lexer "id=123")
  (detail-lexer "123id=123")
  (detail-lexer "id=123name=2")
  )


(defn all-lexer [val] 
  ( re-matches all-reg val))

(comment
  (all-lexer "*")
  (all-lexer "qwerty")
  )

(defn sort-lexer [val]
  (re-matches sort-reg val))

(defn filter-lexer [val]
  (re-matches sort-reg val))

(defn list-lexer [val] (assert false "not implemented"))

(comment
  list-lexer
  )

(defn parse-query-selector [selector]
  )

(comment
  (parse-query-selector "id=123")
  (parse-query-selector "<<sort<<name?(name==Adam#(age<10&(name<>Patrick#age<=100)))")
  )

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