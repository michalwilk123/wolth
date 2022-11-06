(ns wolth.server.path
  (:require [ring.util.codec :refer [url-decode]]
            [wolth.server.-test-data :refer [_test-app-data-w-relations]]
            [wolth.db.fields :refer [normalize-additional-uriql-query]]
            [wolth.db.urisql-parser :refer [apply-uriql-syntax-sugar]]
            [wolth.server.exceptions :refer [throw-wolth-exception]]))

(defn sanitize-uriql-query
  [query]
  (-> query
      (url-decode)
      (apply-uriql-syntax-sugar)))

(defn create-query-name [model-name] (format "%s-query" model-name))

(defn get-query-urls-in-order
  [objects-names query-params]
  (when (not= (count objects-names) (count query-params))
    (throw-wolth-exception
      :400
      "Cannot parse all parameters needed to build the query"))
  (map (fn [it]
         (->> it
              (create-query-name)
              (keyword)
              (get query-params)))
    objects-names))

(comment
  (get-query-urls-in-order (list "User") {:User-query "*"})
  (get-query-urls-in-order (list "Country" "City")
                           {:Country-query "filter(\"countryName\"=='Poland')",
                            :City-query "filter(\"cityName\"<>'Gdansk')"}))

;; additional-subqueries (map :additional-query serializer-specs)
;; query (first
;;         (map str
;;           (map server-utils/sanitize-uriql-query (vals path-params))
;;           additional-subqueries))


(defn normalize-additional-query
  [ctx operation]
  (if-let [query (get-in operation [:additional-query])]
    (normalize-additional-uriql-query ctx query)
    nil))

(comment
  (normalize-additional-query {}
                              {:fields ["countryName" "code" "president"
                                        "cities"],
                               :additional-query "filter(\"code\"<>'11111')",
                               :model-fields ["cities"]}))
(comment
  (normalize-additional-query
    {:logged-user {:username "Domino"}}
    {:fields ["countryName" "code" "president" "cities"],
     :additional-query "filter(\"code\"<><:user-username>)",
     :model-fields ["cities"]}))

(def _test-operation-chain
  (list {:fields ["countryName" "code" "president" "cities"],
         :additional-query "filter(\"code\"<>'11111')",
         :model-fields ["cities"]}
        {:fields ["cityName" "major"],
         :additional-query
           "filter(\"major\"<>'Adam West'and\"author\"==<:user-id>)",
         :model-fields ["country"]}))

(defn normalize-path-parameters
  [ctx objects-data serializer-operations params]
  (when-not (empty? params)
    (let [table-names (map :name objects-data)
          additional-queries (map (partial normalize-additional-query ctx)
                               serializer-operations)]
      (as-> params it
        (get-query-urls-in-order table-names it)
        (map sanitize-uriql-query it)
        (map str it additional-queries)))))

(comment
  (normalize-path-parameters {}
                             ((comp list first :objects)
                               _test-app-data-w-relations)
                             (take 1 _test-operation-chain)
                             {})
  (normalize-path-parameters {}
                             ((comp list first :objects)
                               _test-app-data-w-relations)
                             (take 1 _test-operation-chain)
                             {:Country-query "*"})
  (normalize-path-parameters
    {:logged-user {:id 997}}
    ((comp (partial take 2) :objects) _test-app-data-w-relations)
    _test-operation-chain
    {:City-query "*", :Country-query "filter(\"countryName\"=='USA')"}))