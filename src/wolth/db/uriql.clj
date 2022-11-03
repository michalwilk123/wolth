(ns wolth.db.uriql
  (:require clojure.walk
            [wolth.db.urisql-parser :refer [parse-uriql merge-where-clauses]]
            [wolth.server.exceptions :refer [throw-wolth-exception]]
            [wolth.utils.common :refer
             [concat-vec-field-in-maps concatv remove-nil-vals-from-map]]))


(def test-select-query-1
  {:select (list :value1 :value2 :value3),
   :from :FirstTable,
   :where [:and
           [:or [:and [:> :ll "100"] [:> :qq "30"] [:<> :mm "1"] [:<> :oo "10"]]
            [:>= :cc "10"]] [:= :aa "bb"]]})

(def test-select-query-2
  {:select :*,
   :from :SecondTable,
   :where [:<> :role "admin"],
   :order-by [[:name :desc] [:age :asc]]})

(defn concat-as-keyword
  [as-clause kw]
  (->> kw
       (name)
       (str as-clause ".")
       (keyword)))

(comment
  (concat-as-keyword "testKW" :id))

(defn hydrate-from-clause
  [as-kw query]
  (assoc query :from (vector (vector (query :from) (keyword as-kw)))))

(defn hydrate-fields-clause
  [as-kw query]
  (let [[kw-to-change fields] (first query)]
    (assoc query
      kw-to-change (if (keyword? fields)
                     (concat-as-keyword as-kw fields)
                     (map (partial concat-as-keyword as-kw) fields)))))

(defn hydrate-where-clause
  [as-kw query]
  (letfn [(build-where-cl [item]
            (cond (vector? item) (vec (cons (first item)
                                            (map build-where-cl (rest item))))
                  (keyword? item) (concat-as-keyword as-kw item)
                  :else item))]
    (update-in query [:where] build-where-cl)))

(defn hydrate-sort-clause
  [as-kw query]
  (update query
          :order-by
          #(and
             %
             (mapv (fn [sort-q]
                     (update-in sort-q [0] (partial concat-as-keyword as-kw)))
               %))))

#_"NOT READY YET"
(defn hydrate-set-clause
  [as-kw query]
  (update
    query
    :set
    (fn [it]
      (and (map? it)
           (->> it
                (map (fn [[k val]] (vector (concat-as-keyword as-kw k) val)))
                (into {}))))))

(comment
  (hydrate-from-clause "AS_TEST" test-select-query-1)
  (hydrate-fields-clause "AS_TEST" test-select-query-1)
  (hydrate-where-clause "AS_TEST" test-select-query-1)
  (hydrate-where-clause "AS_TEST" test-select-query-2)
  (hydrate-sort-clause "AS_TEST" test-select-query-2)
  (hydrate-sort-clause "AS_TEST" test-select-query-1))

#_" We hydrate each query with AS TABLE clause.
   We change the strategy of the hydration depanding on the query type.
   UPDATE has SET clause, DELETE has no field list, all queries have WHERE clause etc..
   "
(defn hydrate-query-with-as-clause
  [query as-name]
  (let [-hydrate-from-clause (partial hydrate-from-clause as-name)
        -hydrate-fields-clause (partial hydrate-fields-clause as-name)
        -hydrate-where-clause (partial hydrate-where-clause as-name)
        -hydrate-set-clause (partial hydrate-set-clause as-name)
        -hydrate-sort-clause (partial hydrate-sort-clause as-name)
        field-exists? (partial get query)]
    (cond
      (field-exists? :select) ((comp -hydrate-from-clause
                                     -hydrate-fields-clause
                                     -hydrate-where-clause
                                     -hydrate-sort-clause)
                                query)
      (field-exists? :update) ((comp -hydrate-from-clause
                                     -hydrate-fields-clause
                                     -hydrate-set-clause
                                     -hydrate-where-clause)
                                query)
      :else (throw (RuntimeException.
                     "Tried to hydrate unsupported query type")))))


(comment
  (hydrate-query-with-as-clause test-select-query-1 "TEST")
  (hydrate-query-with-as-clause test-select-query-2 "TEST"))

(defn- hydrate-join-fields
  [fields as-names table-names]
  (letfn [(-hydrate-fields [names fields tab-name]
            (merge fields
                   {:joint (mapv concat-as-keyword names (fields :joint)),
                    :tab-data (vector tab-name
                                      (-> names
                                          (second)
                                          (keyword)))}))]
    (map -hydrate-fields (partition 2 1 as-names) fields (rest table-names))))

(comment
  (hydrate-join-fields (list {:joint [:id :author]})
                       (list "T1" "T2")
                       (list :User :Task))
  (hydrate-join-fields (list {:joint [:id :assignmentId]}
                             {:joint [:classId :id]})
                       (list "T1" "T2" "T3")
                       (list :User :Assignment :Class)))

(defn- merge-select-simple
  [queries]
  (->> {:select (concat-vec-field-in-maps queries :select),
        :from (first (map :from queries)),
        :order-by (concat-vec-field-in-maps queries :order-by),
        :where (apply (partial merge-where-clauses :and) (map :where queries))}
       (remove-nil-vals-from-map)))

(comment
  (merge-select-simple
    (list {:select :T1.*,
           :from [:FirstTable :T1],
           :order-by [[:T2.name :desc]],
           :where [:> :T1.ll 100]}
          {:select :T2.*, :from [:SecTable :T2], :where [:= :T2.aa "qqq"]}))
  (merge-select-simple
    (list {:select :T1.*, :from [:FirstTable :T1], :order-by [[:T2.name :desc]]}
          {:select :T2.*, :from [:SecTable :T2]}))
  (merge-select-simple (list {:select :T1.*,
                              :from [:FirstTable :T1],
                              :order-by [[:T2.name :desc]],
                              :where [:> :T1.ll 100]}
                             {:select :T2.*, :from [:SecTable :T2]})))

(defn- join-map-to-clause
  [join-data]
  [(join-data :tab-data) (into [:=] (join-data :joint))])

(comment
  (join-map-to-clause {:joint [:T1.id :T2.author], :tab-data [:Task :T2]}))

(defn left-join?
  [join-field]
  (when (re-matches #"(?i)T[0-9]+\.id"
                    (-> join-field
                        (get-in [:joint 0])
                        (name)))
    true))

(comment
  (left-join? {:joint [:T1.id :T2.author], :tab-data [:SecondTable :T2]}))

(defn- hydrate-queries-with-joins
  [join-fields query]
  (let [[l-joins r-joins] (reduce (fn [acc el]
                                    (if (left-join? el)
                                      (update acc 0 (fn [l] (conj l el)))
                                      (update acc 1 (fn [l] (conj l el)))
                                      ))
                            [[] []]
                            join-fields)]
    (cond-> query
      (not-empty l-joins) (assoc :left-join
                            (concatv (map join-map-to-clause l-joins)))
      (not-empty r-joins) (assoc :right-join
                        (concatv (map join-map-to-clause r-joins))))))

(comment
  (hydrate-queries-with-joins
    (list {:joint [:T1.id :T2.author], :tab-data [:SecondTable :T2]}
          {:joint [:T2.id :T3.field], :tab-data [:ThirdTable :T3]})
    {}))

(defn join-queries
  [queries join-fields]
  (assert (= (count queries) (inc (count join-fields))))
  (let [as-kws (take (count queries) (map #(str "T" %) (iterate inc 1)))
        table-names (map :from queries)
        hydrated-queries (map hydrate-query-with-as-clause queries as-kws)
        hydrated-fields (hydrate-join-fields join-fields as-kws table-names)]
    (->> hydrated-queries
         (merge-select-simple)
         (hydrate-queries-with-joins hydrated-fields))))

(comment
  (join-queries (list test-select-query-1 test-select-query-2)
                (list {:joint [:id :author]})))

;; (defn- translate-string-fields-into-keywords
;;   [sub-query]
;;   (cond (and (vector? sub-query) (.contains [:and :or] (first sub-query)))
;;           (mapv translate-string-fields-into-keywords sub-query)
;;         (vector? sub-query) (update-in sub-query [1] keyword)
;;         (keyword? sub-query) sub-query
;;         :else (throw (RuntimeException.
;;                        "Syntax error in filter query for serializers"))))

;; (comment
;;   (translate-string-fields-into-keywords [:= "owner" "Michał"])
;;   (translate-string-fields-into-keywords
;;     [:and [:or [:> "age" 100] [:= "owner" "Michał"]] [:= "name" "John"]]))


;; (defn attach-optional-filter-query
;;   [subquery filter-query]
;;   (if filter-query
;;     (update-in subquery [:where] (partial merge-where-clauses filter-query))
;;     subquery))

;; (comment
;;   (attach-optional-filter-query {:where [:or [:= :role "regular"]
;;                                          [:<> :surname "Kowalski"]]}
;;                                 [:= "owner" "Michał"])
;;   (attach-optional-filter-query {:where [:and [:= :role "regular"]
;;                                          [:<> :surname "Kowalski"]]}
;;                                 [:= "owner" "Michał"])
;;   (attach-optional-filter-query {} [:= "owner" "Michał"]))


(defn build-single-hsql-map
  [type table-name selector-str & [fields]]
  (let [table-kw (keyword table-name)]
    (case type
      :select (-> {:select (or fields :*), :from table-kw}
                  (merge (parse-uriql selector-str
                                      #{"filter" "sorta" "sortd"})))
      :insert {:insert-into table-kw, :values [fields]}
      :update (-> {:update table-kw}
                  (merge (parse-uriql selector-str #{"filter"}))
                  (update :set (partial merge fields)))
      :delete (-> {:delete-from table-kw}
                  (merge (parse-uriql selector-str #{"filter"})))
      (throw-wolth-exception :500 (str "Unknown query type: " type)))))

(comment
  (build-single-hsql-map :update "person" "filter(\"name\"=='admin')")
  (build-single-hsql-map :select "person"
                         "sorta(\"name\")filter(\"role\"=='regular')"
                           '(:username :email)))

;; to finish in some future. For now joins are implemented only on selects
(defn merge-hsql-queries
  [type rels queries]
  (let [amount-of-queries (count queries)]
    (if (= amount-of-queries 1)
      (-> queries
          (first))
      (do (assert (= amount-of-queries (inc (count rels))))
          (case type
            :select (join-queries queries rels)
            (throw-wolth-exception
              :500
              (str "Don't know how to merge queries with type: " type)))))))

(comment
  (merge-hsql-queries
    :select
    (list {:joint [:id :country_id], :field-to-inject :cities})
    (list {:select (list :countryName :code :president :cities),
           :from :Country,
           :where [:= :countryName "Poland"]}
          {:select (list :cityName :major),
           :from :City,
           :where [:<> :cityName "Gdansk"]})))

