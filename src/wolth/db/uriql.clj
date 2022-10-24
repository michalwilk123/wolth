(ns wolth.db.uriql
  (:require [clojure.string :as str]
            clojure.walk
            [honey.sql.helpers :as h]
            [wolth.server.exceptions :refer [throw-wolth-exception]]
            [wolth.utils.common :refer [get-first-matching-pred]]))


(def open-bracket "(")
(def close-bracket ")")

; Available operations:
; - sort "<<" ">>"
; - filter - znaki: "==" "<>" ">" ">=" "LEN".
;    Połączone za pomocą
; - * (all)
; - detail - zwykły znak "="

(defmacro def-token
  [name & exprs]
  `(def ~name (re-pattern (format "(?<%s>%s)" '~name (str ~@exprs)))))

(comment
  (clojure.walk/macroexpand-all '(def-token numer #"cxzcxzcxz" "dsads")))

(def-token fieldToken #"[A-Za-z]+[0-9]*")
(def-token valueToken #"[A-Za-z0-9\- ]+")
(def-token detailToken fieldToken "=" valueToken)
(def-token allToken #"\*")
(def-token sortDirToken #"(<<)|(>>)")
(def-token sortToken sortDirToken fieldToken)
(def-token filterDelimiterToken "\\$(and|or)\\$")
(def-token filterOperatorToken "<|>|(<=)|(>=)|(<>)|(==)")
(def-token filterExprToken fieldToken filterOperatorToken valueToken)
(def-token filterBracketToken #"\(|\)")
(def-token sortExprToken sortToken "+")

(defn token-found?
  [pat text & [exact]]
  (if exact (some? (re-matches pat text)) (some? (re-find pat text))))

(comment
  (token-found? fieldToken "dsdadsa12" :exact)
  (token-found? detailToken "id=123" :exact)
  (token-found? sortToken "<<name")
  (token-found? sortToken "<<name>>age" :exact)
  (token-found? sortToken "<<name>>age" :exact)
  (token-found? sortExprToken "<<name>>age" :exact)
  (token-found? sortExprToken "<<name>>age>" :exact)
  (token-found? filterExprToken "name==1" :exact)
  (token-found? filterDelimiterToken "$or$" :exact)
  (token-found? filterOperatorToken "<" :exact)
  (str/replace "<<name>>age(dsadsa==1$or$ccc<=21)" sortToken ""))


#_"THIS TOKEN IS FOR INTERNAL PARSER PURPOSES! DO NOT USE IT!"
(def filterQueryCandidateRe
  (re-pattern
    (str/join "|" [filterExprToken filterDelimiterToken filterBracketToken])))

(def-token fullFilterQueryExpr
           open-bracket
           (re-pattern (str/join "|"
                                 [fieldToken filterOperatorToken valueToken
                                  filterDelimiterToken filterBracketToken]))
           close-bracket
           "{3,}")

(comment
  (filterQueryCandidateRe)
  (fullFilterQueryExpr)
  (re-seq filterQueryCandidateRe "dsadsa==1$or$ccc<=21")
  (token-found? fullFilterQueryExpr "name==Adam$and$age>10" :exact)
  (token-found? fullFilterQueryExpr "(name==Adam$and$surname<>kowalski)" :exact)
  (token-found? fullFilterQueryExpr "name==Adam" :exact)
  (token-found? fullFilterQueryExpr "<<name" :exact)
  (token-found? fullFilterQueryExpr "id==58 123-121-233" :exact)
  (str/replace "name==Adam$and$age>10" fullFilterQueryExpr "QQQ")
  (re-matches #"(?<dsads>(<|>|(<=))+)" "<<<="))


(defn parse-tokens-from-text
  [text token-pat & token-kwords]
  (assert string? text)
  (assert (every? keyword? token-kwords))
  (let [mat (re-matcher token-pat text)]
    (when (.matches mat)
      (into {} (for [i token-kwords] [i (.group mat (name i))])))))

(comment
  (parse-tokens-from-text "username=Michal" detailToken :fieldToken :valueToken)
  (parse-tokens-from-text "<<name" sortToken :fieldToken :sortDirToken))


(defn split-filter-query-into-chunks
  [filterQuery]
  (map first (re-seq filterQueryCandidateRe filterQuery)))

(comment
  (split-filter-query-into-chunks
    "name==Adam$and$(sul==smazona$or$cukier==chlodzony)"))

(defn filter-operator-to-kword
  [op]
  (case op
    "==" :=
    (keyword op)))

(comment
  (filter-operator-to-kword "==")
  (filter-operator-to-kword "<>"))


(defn filter-delim-to-kword
  [de]
  (case de
    "$and$" :and
    "$or$" :or
    (assert
      false
      (format
        "Unknown symbol: %s! Cannot translate to proper sql logical operator"
        de))))

(comment
  (filter-delim-to-kword "$adsand$")
  (filter-delim-to-kword "$and$"))

(defn evaluate-single-comp
  [comp]
  (if-not (string? comp)
    comp
    (let [parsed (parse-tokens-from-text comp
                                         filterExprToken
                                         :filterOperatorToken
                                         :fieldToken
                                         :valueToken)]
      (vector (filter-operator-to-kword (parsed :filterOperatorToken))
              (keyword (parsed :fieldToken))
              (parsed :valueToken)))))

(comment
  (evaluate-single-comp "cukier==chlodzony"))

(defn evaluate-single-oper
  "First component should only contain nested data"
  [comp1 delim comp2]
  (let [comp1 (evaluate-single-comp comp1)
        comp2 (evaluate-single-comp comp2)
        delim (filter-delim-to-kword delim)]
    (if (-> comp1 ; we are reducing at this point, the nesting of the where
                  ; clauses
            (first)
            (= delim))
      (conj comp1 comp2)
      (vector delim comp1 comp2))))

(comment
  (evaluate-single-oper "cukier==chlodzony" "$or$" "sul==smazona")
  (evaluate-single-oper [:and [:> :age 20] [:<= :height "200"]]
                        "$or$"
                        "sul==smazona")
  (evaluate-single-oper [:or [:> :age 20] [:<= :height "200"]]
                        "$or$"
                        "sul==smazona"))

(defn evaluate-flat-filter-query
  [*words]
  (let [words (split-at 3 *words)
        num-of-syms-to-eval (count (first words))
        evaluated-expr (if (= num-of-syms-to-eval 3)
                         (apply evaluate-single-oper (first words))
                         (evaluate-single-comp (first (first words))))
        to-concat (second words)]
    (if-not (empty? to-concat)
      (recur (conj to-concat evaluated-expr))
      evaluated-expr)))

(comment
  (evaluate-flat-filter-query '("a==1" "$or$" "b==2"))
  (evaluate-flat-filter-query '("a==1"))
  (evaluate-flat-filter-query '("a==1" "$or$" "b==2" "$and$" "cc==3"))
  (evaluate-flat-filter-query '("a==1" "$or$" "b==2" "$or$" "cc==3")))


(defn reduce-stack
  [p-stack]
  (let [operation-to-reduce (take-while (partial not= "(") p-stack)]
    (conj (rest (drop-while (partial not= "(") p-stack))
          (evaluate-flat-filter-query operation-to-reduce))))


(defn surrounded-by-brackets
  [coll]
  (and (= (first coll) open-bracket) (= (last coll) close-bracket)))

(comment
  (surrounded-by-brackets '("(" 1111 "111" 22 ")"))
  (surrounded-by-brackets '("(" 1111 "111" 22 "3333")))

(defn filter-parser
  [*exprs]
  (let [exprs (if (not (surrounded-by-brackets *exprs))
                (flatten (list open-bracket *exprs close-bracket))
                *exprs)
        p-stack (atom '())]
    (doall
      (for [el exprs]
        (if (= el ")") (swap! p-stack reduce-stack) (swap! p-stack conj el))))
    (assert (= (count @p-stack) 1))
    (first @p-stack)))

(defn hydrate-filter-query-w-table-name
  [query table-name & {:keys [allow-string-fields]}]
  (let [field (second query)]
    (if (or (keyword? field) allow-string-fields)
      (assoc query 1 (keyword (str table-name "." (name field))))
      (into []
            (map (fn [x]
                   (if (vector? x)
                     (hydrate-filter-query-w-table-name x
                                                        table-name
                                                        :allow-string-fields
                                                        allow-string-fields)
                     x))
              query)))))

(comment
  (hydrate-filter-query-w-table-name [:= :name "Adam"] "Person")
  (hydrate-filter-query-w-table-name [:= "id" "Adam"]
                                     "Person"
                                     :allow-string-fields
                                     true)
  (hydrate-filter-query-w-table-name [:= "name" "Adam"] "Person")
  (hydrate-filter-query-w-table-name [:or [:> :age 20] [:= :name "Adam"]]
                                     "Person"))

; zawijający parser
(defn parse-filter-expr
  [expr table-name]
  (-> expr
      (split-filter-query-into-chunks)
      (filter-parser)
      (hydrate-filter-query-w-table-name table-name)))

(comment
  (parse-filter-expr "name==Adam" "Person")
  (parse-filter-expr "(name==Adam$and$(sul==smazona$or$cukier==chlodzony))"
                     "Person")
  (parse-filter-expr
    "(aa==bb$and$cc>=10$or$oo<>10$and$(mm<>1$and$qq>30$and$(ll=1$and$ll>100)))"
    "TableName")
  (parse-filter-expr "aa==bb$or$cc>=10$or$oo<>10$or$qq>=888" "TableName")
  (parse-filter-expr "(date<1$and$name<>nazwa)$or$age>=1" "Movie"))

(defn filter-builder
  [table-name expr]
  {:where (parse-filter-expr expr table-name)})

(comment
  (filter-builder "Movie" "(date<1$and$name<>nazwa)$or$age>=1"))

(defn create-sql-selector
  [table-name field]
  (keyword (str table-name "." field)))

(defn detail-builder
  [table-name selector]
  (let [parsed-vals
          (parse-tokens-from-text selector detailToken :fieldToken :valueToken)]
    {:where [:= (create-sql-selector table-name (parsed-vals :fieldToken))
             (parsed-vals :valueToken)]}))

(comment
  (detail-builder "aaaa" "id=123"))

(defn sort-builder
  [table-name selector]
  (letfn [(single-sort-builder [single-selector]
            (let [parsed-vals (parse-tokens-from-text (first single-selector)
                                                      sortToken
                                                      :fieldToken
                                                      :sortDirToken)]
              (vector (parsed-vals :fieldToken)
                      (case (parsed-vals :sortDirToken)
                        ">>" :asc
                        "<<" :desc))))]
    (->> (re-seq sortToken selector)
         (map single-sort-builder)
         (map (fn [x] (assoc x 0 (keyword (str table-name "." (first x))))))
         (apply h/order-by))))

(comment
  (sort-builder "person" "<<name>>age"))

(defn multiple-token-found
  [query & tokens]
  (some? (reduce (fn [text pattern]
                   (if (and (some? text) (some? (re-find pattern text)))
                     (str/replace text pattern "")
                     nil))
           query
           tokens)))

(comment
  (multiple-token-found "<<name(name<>admin)" sortToken filterQueryCandidateRe)
  (multiple-token-found "<<name(name<>admin)" sortToken filterQueryCandidateRe)
  (multiple-token-found "1111ddd" #"[0-9]+" #"[a-z]+")
  (multiple-token-found "13232132189321" #"[0-9]+" #"[a-z]+"))

(defn attach-optional-filter-query
  [subquery table-name filter-query]
  (if filter-query
    (update-in subquery
               [:where]
               (fn [current]
                 (let [hydrated-query (hydrate-filter-query-w-table-name
                                        filter-query
                                        table-name
                                        :allow-string-fields
                                        true)]
                   (cond (= (first current) :and) (conj current hydrated-query)
                         (vector? current) (vector :and current hydrated-query)
                         :else hydrated-query))))
    subquery))

(comment
  (attach-optional-filter-query {:where [:or [:= :Person.role "regular"]
                                         [:<> :Person.surname "Kowalski"]]}
                                "Person"
                                [:= "owner" "Michał"])
  (attach-optional-filter-query {:where [:and [:= :Person.role "regular"]
                                         [:<> :Person.surname "Kowalski"]]}
                                "Person"
                                [:= "owner" "Michał"])
  (attach-optional-filter-query {} "Person" [:= "owner" "Michał"]))

(defn build-selector-query
  [table-name selector &
   {:keys [sort-clause all-clause filter-clause],
    :or {all-clause true, filter-clause true, sort-clause true}}]
  (assert (and (string? table-name) (string? selector))
          "Both arguments must be a string!")
  (letfn
    [(all-clause-parser []
       (and all-clause (token-found? allToken selector :exact) {}))
     (sort-and-filter-parser []
       (and sort-clause
            filter-clause
            (multiple-token-found selector sortToken filterQueryCandidateRe)
            (merge (sort-builder table-name selector)
                   (filter-builder table-name selector))))
     (sort-parser []
       (and sort-clause
            (token-found? sortExprToken selector :exact)
            (sort-builder table-name selector)))
     (filter-parser []
       (and filter-clause
            (token-found? fullFilterQueryExpr selector :exact)
            (filter-builder table-name selector)))
     (syntax-error []
       (throw-wolth-exception :400
                              (format "Uriql syntax error. Unknown input: %s"
                                      selector)))]
    (get-first-matching-pred [all-clause-parser sort-and-filter-parser
                              sort-parser filter-parser syntax-error])))

(comment
  (build-selector-query "Person" "name==michal")
  (build-selector-query "Person" "(name==michal$and$surname<>kowalski)")
  (build-selector-query "Person" "(name==Adam$and$surname<>kowalski)")
  (build-selector-query "Person" "*")
  (build-selector-query "Person" "<<name(name==michal$and$id<>123)")
  (build-selector-query "Person" "<<name>>surname"))

(defn build-select
  [table-name selector filter-query & [fields]]
  (-> {:select (or fields :*), :from (keyword table-name)}
      (merge (build-selector-query table-name selector))
      (attach-optional-filter-query table-name filter-query)))

(comment
  (build-select "Person" "id==111" nil)
  (build-select "Person" "adsadsa" nil)
  (build-select "Person" "(name<>111)" [:= "hidden" false])
  (build-select "Person" "id==111" [:= "hidden" false])
  (build-select "Person" "<<name>>age" nil)
  (build-select "Person" "<<name" nil)
  (build-select "Person" "<<name" [:= "id" 997])
  (build-select "Person" "*" nil)
  (build-select "User" "*" nil (list :author :content :id))
  (build-select "Person" "<<name(name<>admin)" nil)
  (build-select "Person" "(name==Adam$and$age>10)" nil))

(defn build-update
  [table-name selector filter-query]
  (-> {:update (keyword table-name)}
      (merge (build-selector-query table-name selector :sort-clause false))
      (attach-optional-filter-query table-name filter-query)))

(comment
  (build-update "person" "name==admin" nil)
  (build-update "person" ; should throw syntax error because sorts are not
                         ; permitted
                "<<createdAt"
                [:= "id" 112])
  (build-update "person" "*" [:= "id" 112]))


#_"Below functions will be crutial for implementing dynamic joins.
   For now i left them not completed"
(defn merge-select-hsql [queries] (first queries))

(defn merge-update-hsql
  [queries set-vals]
  (assoc (first queries) :set set-vals))

(comment
  (merge-update-hsql (list (build-update "person" "*" [:= "id" 112]))
                     {:email "nowy@mail.com"}))


(defn build-delete
  [table-name selector filter-query]
  (-> {:delete-from (keyword table-name)}
      (merge (build-selector-query table-name selector :sort-clause false))
      (attach-optional-filter-query table-name filter-query)))

(comment
  (build-delete "person" "name<>michal" nil)
  (build-delete "person" "*" nil)
  (build-delete "person" "aaaa" nil)
  (build-delete "person" "*" [:= "surname" "Kowalski"]))


;; (defn build-query-from-url
;;   [url-string]
;;   (swap! objects-url-spec assoc :person [[:id] [:name :string] [:age :int]])
;;   (->> url-string
;;        (#(str/split % #"/"))
;;        (remove str/blank?)
;;        (create-pairs)
;;        (check-if-joining-valid)
;;        (map (partial apply build-selector-query))
;;        ;;  (merge-sql-subqueries)
;;   ))


;; (defn transform-query-into-map
;;   [query]
;;   (let [q-splitted (rest (str/split query #"/"))
;;         view-name (last q-splitted)
;;         subqueries (partition 2 2 q-splitted)]
;;     {:view view-name, :subqueries subqueries}))

;; (comment
;;   (transform-query-into-map "/Person/id=1/Post/*/admin"))

;; (def _test-app-data {})

;; (defn merge-subqueries
;;   [app-data s-queries]
;;   (throw (RuntimeException. "not implemented yet"))
;;   (map #(apply build-select %) s-queries))

;; (comment
;;   (merge-subqueries {}
;;                     ((transform-query-into-map "/Person/id=1/Post/*/admin")
;;                       :subqueries)))

