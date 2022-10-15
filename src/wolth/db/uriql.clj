(ns wolth.db.uriql
  (:require [clojure.string :as str]
            clojure.walk
            [honey.sql.helpers :as h]))


(def open-bracket "(")
(def close-bracket ")")

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

(defmacro def-token
  [name & exprs]
  `(def ~name (re-pattern (format "(?<%s>%s)" '~name (str ~@exprs)))))



(comment
  (clojure.walk/macroexpand-all '(def-token numer #"cxzcxzcxz" "dsads")))

(def-token fieldToken #"[A-Za-z]+[0-9]*")
(def-token valueToken #"[A-Za-z0-9]+")
(def-token detailToken fieldToken "=" valueToken)
(def-token allToken #"\*")
(def-token sortDirToken #"(<<)|(>>)")
(def-token sortToken sortDirToken fieldToken)
(def-token filterDelimiterToken "\\$(and|or)\\$")
(def-token filterOperatorToken "<|>|(<=)|(>=)|(<>)|(==)")
(def-token filterExprToken fieldToken filterOperatorToken valueToken)
(def-token filterTerminalToken #"\(|\)")
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
  (str/replace "<<name>>age(dsadsa==1$or$ccc<=21)" sortToken "")
  ; TODO: TO PONIZEJ NIE DZIAŁA!!!
)


(def filterQueryCandidateRe
  (re-pattern
    (str/join "|" [filterExprToken filterDelimiterToken filterTerminalToken])))

(def-token fullFilterQueryExpr
           open-bracket
           filterQueryCandidateRe
           close-bracket
           "{2,}")

(comment
  (filterQueryCandidateRe)
  (fullFilterQueryExpr)
  (re-seq filterQueryCandidateRe "dsadsa==1$or$ccc<=21")
  (token-found? filterQueryCandidateRe "name==john")
  (token-found? filterQueryCandidateRe "(name==john$and$age>20)" :exact)
  (token-found? filterQueryCandidateRe
                "(name<>john$or$(age>=30$and&age<100))"
                :exact)
  (token-found? fullFilterQueryExpr "name==Adam$and$age>10" :exact)
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


(defn build-selector-query
  [table-name selector]
  (assert (and (string? table-name) (string? selector))
          "Both arguments must be a string!")
  (cond-> {}
    (token-found? detailToken selector :exact) (merge (detail-builder table-name
                                                                      selector))
    (token-found? allToken selector :exact) (identity)
    (token-found? sortExprToken selector :exact) (merge (sort-builder table-name
                                                                      selector))
    (token-found? filterQueryCandidateRe selector :exact)
      (merge (filter-builder table-name selector))
    (multiple-token-found selector sortToken filterQueryCandidateRe)
      (merge (sort-builder table-name selector)
             (filter-builder table-name selector))))

(comment
  (build-selector-query "Person" "name==michal")
  (token-found? filterQueryCandidateRe "name==michal"))

(defn build-select
  [table-name selector filter-query & [fields]]
  (let [subquery (merge {:select (or fields :*), :from (keyword table-name)}
                        (build-selector-query table-name selector))]
    (if filter-query
      (update-in subquery
                 [:where]
                 (fn [current]
                   (let [hydrated-query (hydrate-filter-query-w-table-name
                                          filter-query
                                          table-name
                                          :allow-string-fields
                                          true)]
                     (if-not current
                       hydrated-query
                       (vector :and current hydrated-query)))))
      subquery)))

(comment
  (build-select "Person" "id=111" nil)
  (build-select "Person" "(name<>111)" [:= "hidden" false])
  (build-select "Person" "id=111" [:= "hidden" false])
  (build-select "Person" "<<name>>age" nil)
  (build-select "Person" "<<name" nil)
  (build-select "Person" "<<name" [:= "id" 997])
  (build-select "Person" "*" nil)
  (build-select "User" "*" nil (list :author :content :id))
  (build-select "Person" "<<name(name<>admin)" nil)
  (build-select "Person" "name==Adam$and$age>10" nil))

(defn build-update
  [table-name selector values filter-query]
  (let [subquery (merge {:update (keyword table-name), :set values}
                        (build-selector-query table-name selector))]
    (if filter-query
      (update-in subquery
                 [:where]
                 (fn [current]
                   (let [hydrated-query (hydrate-filter-query-w-table-name
                                          filter-query
                                          table-name
                                          :allow-string-fields
                                          true)]
                     (if-not current
                       hydrated-query
                       (vector :and current hydrated-query)))))
      subquery)))

(comment
  (build-update "person"
                "name==admin"
                {:name "nowa nazwa", :email "nowy@mail.pl"}
                nil)
  (build-update "person"
                "*"
                {:name "nowa nazwa", :email "nowy@mail.pl"}
                [:= "id" 112]))

(defn merge-select-hsql [queries] (first queries))



;; (defn build-delete
;;   [table-name selector]
;;   (build-subquery {:delete :*, :from (keyword table-name)} table-name
;;   selector))

;; (comment
;;   (build-delete "person" "id=111"))


(defn build-query-from-url
  [url-string]
  (swap! objects-url-spec assoc :person [[:id] [:name :string] [:age :int]])
  (->> url-string
       (#(str/split % #"/"))
       (remove str/blank?)
       (create-pairs)
       (check-if-joining-valid)
       (map (partial apply build-selector-query))
       ;;  (merge-sql-subqueries)
  ))


(defn transform-query-into-map
  [query]
  (let [q-splitted (rest (str/split query #"/"))
        view-name (last q-splitted)
        subqueries (partition 2 2 q-splitted)]
    {:view view-name, :subqueries subqueries}))

(comment
  (transform-query-into-map "/Person/id=1/Post/*/admin"))

(def _test-app-data {})

(defn merge-subqueries
  [app-data s-queries]
  (throw (RuntimeException. "not implemented yet"))
  (map #(apply build-select %) s-queries))

(comment
  (merge-subqueries {}
                    ((transform-query-into-map "/Person/id=1/Post/*/admin")
                      :subqueries)))

