(ns wolth.server.utils
  (:require [clojure.string :as str]
            [clojure.set :refer [difference]]
            [ring.util.codec :refer [url-decode]]
            [wolth.db.urisql-parser :refer [apply-uriql-syntax-sugar]]
            [wolth.server.-test-data :refer
             [_server_test_app_data _test-object-spec-with-relations-1
              _test-object-spec-with-relations-2]]
            [wolth.server.config :refer [app-data-container]]
            [wolth.server.exceptions :refer
             [def-interceptor-fn throw-wolth-exception]]
            [wolth.utils.common :as common]
            [wolth.db.fields :as fields]))

(def operations-lut
  {:post :create, :delete :delete, :get :read, :patch :update})

(defn get-associated-app-data!
  [app-name]
  (if-let [app-data (get @app-data-container app-name)]
    app-data
    (throw-wolth-exception :404 (format "Application %s not found" app-name))))

(comment
  (get-associated-app-data! "app"))


(defn get-associated-objects
  [app-data-objects table-names]
  (map (fn [tab-name]
         (common/find-first #(= (get % :name) tab-name) app-data-objects))
    table-names))

(comment
  (get-associated-objects (_server_test_app_data :objects)
                          (list "User" "Person"))
  (get-associated-objects (_server_test_app_data :objects)
                          (list "Person" "User")))


(defn uri->parsed-info
  [uri method]
  (let [splitted-names (str/split uri #"/")
        app-name (second splitted-names)
        serializer-name (last splitted-names)
        tables (case method
                 :get (->> splitted-names
                           (drop-last)
                           (drop 2)
                           (take-nth 2))
                 :post (list (nth splitted-names 2))
                 :delete (list (nth splitted-names 2))
                 :patch (list (nth splitted-names 2))
                 :bank nil)]
    [app-name serializer-name tables]))

(comment
  (uri->parsed-info "/app/Person/public" :post)
  (uri->parsed-info "/person/User/user-regular" :post)
  (uri->parsed-info "/app/Person/firstquery/NextTable/*/public" :get)
  (uri->parsed-info "/app/getPrimes" :bank))

(defn uri->app-name [uri] (second (str/split uri #"/")))

(comment
  (uri->app-name "/app/Person/public")
  (uri->app-name "/app/Person"))

(def-interceptor-fn utility-interceptor-fn
                    [ctx]
                    (assoc ctx
                      :app-name (uri->app-name (get-in ctx [:request :uri]))))

(def utility-interceptor
  {:name ::WOLTH-UTILITY-INTERCEPTOR, :enter utility-interceptor-fn})

(defn app-path->app-name
  [path]
  (-> path
      (str/split #"/")
      (last)
      (str/split #"\.")
      (first)))

(comment
  (app-path->app-name "test/system/person/person.app.edn"))

(defn get-related-serializer-spec
  [objects serializer-operations method]
  (as-> objects it
    (map :name it) ; get object name
    ; find right serializers
    (map (fn [tab-name]
           (common/find-first #(= tab-name (:model %)) serializer-operations))
      it)
    ; assign appropriate operation method
    (map (operations-lut method) it)))

(comment
  (get-related-serializer-spec
    (get-associated-objects (_server_test_app_data :objects)
                            (list "User" "Person"))
    (get-in _server_test_app_data [:serializers 0 :operations])
    :post)
  (get-related-serializer-spec
    (get-associated-objects (_server_test_app_data :objects) (list "User"))
    (get-in _server_test_app_data [:serializers 0 :operations])
    :delete))


(defn sanitize-uriql-query
  [query]
  (-> query
      (url-decode)
      (apply-uriql-syntax-sugar)))


(defn get-query-urls-in-order
  [objects-names query-params]
  (when (not= (count objects-names) (count query-params))
    (throw-wolth-exception
      :400
      "Cannot parse all parameters needed to build the query"))
  (map (fn [it]
         (->> it
              (format "%s-query")
              (keyword)
              (get query-params)))
    objects-names))

(comment
  (get-query-urls-in-order (list "User") {:User-query "*"})
  (get-query-urls-in-order (list "Country" "City")
                           {:Country-query "filter(\"countryName\"=='Poland')",
                            :City-query "filter(\"cityName\"<>'Gdansk')"}))


(defn- get-correct-relation-info
  [fields [l-object r-object]]
  (let [field-to-search (first (difference (set fields)
                                           (set (map #(-> %
                                                          (get :name)
                                                          (keyword))
                                                  (l-object :fields)))))
        l-relation (common/find-first #(= (name field-to-search)
                                          (% :related-name-inside))
                                      (get l-object :relations))
        r-relation (common/find-first #(= (name field-to-search)
                                          (% :related-name-outside))
                                      (get r-object :relations))]
    (-> (cond l-relation {:joint [(l-relation :name) "id"],
                          :field-to-inject field-to-search}
              r-relation {:joint ["id" (r-relation :name)],
                          :field-to-inject field-to-search}
              :else (throw-wolth-exception
                      :500
                      (format "Could not join two objects: %s and %s"
                              l-object
                              r-object)))
        (update :joint (partial mapv keyword)))))

(defn get-serialized-relation-data
  [fields objects-data]
  (map get-correct-relation-info fields (partition 2 1 objects-data)))

(comment
  (get-correct-relation-info [:countryName :code :president :cities]
                             (list _test-object-spec-with-relations-1
                                   _test-object-spec-with-relations-2))
  (get-correct-relation-info [:cityName :major :country]
                             (list _test-object-spec-with-relations-2
                                   _test-object-spec-with-relations-1))
  (get-serialized-relation-data (list [:countryName :code :president :cities]
                                      [:cityName :major])
                                (list _test-object-spec-with-relations-1
                                      _test-object-spec-with-relations-2))
  (get-serialized-relation-data (list [:countryName :president])
                                (list _test-object-spec-with-relations-1)))