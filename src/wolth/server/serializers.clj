(ns wolth.server.serializers
  (:require
    [wolth.utils.common :as utils]
    [wolth.server.exceptions :refer [throw-wolth-exception def-interceptor-fn]]
    [wolth.server.path :refer [normalize-path-parameters]]
    [honey.sql :as sql]
    [wolth.server.utils :as server-utils]
    [wolth.server.-test-data :refer
     [_serializers_test_app_data _test-object-spec
      _test-object-spec-with-relations-1 _test-object-spec-with-relations-2
      _test-bank-request-map _test-post-request-map _test-get-request-map
      _test-patch-request-map _test-delete-request-map]]
    [wolth.server.bank :as bank-utils]
    [wolth.db.uriql :refer [merge-hsql-queries build-single-hsql-map]]
    [wolth.server.config :refer [def-context app-data-container]]
    [wolth.db.fields :as fields]))

(def-context _test-context
             {app-data-container {"test-app" _serializers_test_app_data}})

(defn- serialize-post
  [body-params object-specs]
  (assert (map? body-params))
  (assert (map? object-specs))
  (let [table-name (:name object-specs)
        ;; fields (:fields (first serializer-specs))
        ;; attatched (:attached (first serializer-specs))
        ;; processed-fields (as-> params it
        ;;                    (select-keys it (map keyword fields))
        ;;                    (utils/assoc-vector it attatched))
       ]
    (-> (build-single-hsql-map :insert table-name nil body-params)
        (sql/format))
    ;; (if-let [ ; normalized-fields (assert false)
    ;;          ;;  (fields/normalize-field-associeted-w-object
    ;;          ;;                      processed-fields
    ;;          ;;                      object-specs)
    ;;         ]
    ;;   (throw-wolth-exception :400))
  ))

(comment
  (serialize-post {:password "dmsandkjsanjkdsbnakh", :name "lalalala"}
                  ;; (list {:fields ["id" "username" "email" "password"],
                  ;;        :attached [["role" "regular"] ["username"
                  ;;        :user-name]
                  ;;                   ["id" :random-uuid]]})
                  _test-object-spec))

(defn- serialize-get
  [queries serializer-specs objects-data]
  (assert (sequential? queries))
  (let [serializer-fields (map #(map keyword (% :fields)) serializer-specs)
        model-fields (map :model-fields serializer-specs)
        ;; additional-subqueries (map :additional-query serializer-specs)
        object-names (map #(get % :name) objects-data)
        ;; ordered-path-params (server-utils/get-query-urls-in-order
        ;; object-names
        ;;                                                           path-params)
        relations-data (server-utils/get-serialized-relation-data model-fields
                                                                  objects-data)
        ;; queries (map str
        ;;           (map server-utils/sanitize-uriql-query ordered-path-params)
        ;;           additional-subqueries)
       ]
    (->> (map (partial build-single-hsql-map :select)
           object-names
           queries
           serializer-fields)
         (merge-hsql-queries :select relations-data)
         (sql/format)
         (assoc {:relation-fields (map :field-to-inject relations-data),
                 :table-names object-names}
           :sql-query))))

(comment
  (serialize-get (list "filter(\"name\"=='John')filter(\"role\"=='regular')")
                 (list {:fields ["username" "email"]})
                 (list _test-object-spec))
  (serialize-get (list "" "")
                 (list {:fields ["countryName" "code" "president"],
                        :model-fields ["cities"]}
                       {:fields ["cityName" "major"]})
                 (list _test-object-spec-with-relations-1
                       _test-object-spec-with-relations-2))
  (serialize-get
    (list "filter(\"countryName\"=='Poland')filter(\"code\"<>'11111')"
          "filter(\"cityName\"<>'Gdansk')filter(\"major\"<>'Adam West')")
    (list {:fields ["countryName" "code" "president"], :model-fields ["cities"]}
          {:fields ["cityName" "major"]})
    (list _test-object-spec-with-relations-1
          _test-object-spec-with-relations-2)))


(defn- serialize-patch
  [uriql-query body-params object-data]
  (assert (map? body-params))
  (assert (map? object-data))
  (let [object-names (object-data :name)
        ;; fields (:fields (first serializer-specs))
        ;; attatched (:attatched (first serializer-specs))
        ;; additional-subqueries (map :additional-query serializer-specs)
        ;; processed-fields (as-> body-params it
        ;;                    (select-keys it (map keyword fields))
        ;;                    (utils/assoc-vector it attatched))
        ;; query (first
        ;;         (map str
        ;;           (map server-utils/sanitize-uriql-query (vals path-params))
        ;;           additional-subqueries))
       ]
    (-> (build-single-hsql-map :update object-names uriql-query body-params)
        (sql/format))
    ;; (if-let [;; normalized-fields nil
    ;;          ;;  (fields/normalize-field-associeted-w-object
    ;;          ;;                      processed-fields
    ;;          ;;                      object-specs)
    ;;         ]
    ;;   (throw-wolth-exception :400))
  ))


(comment
  (serialize-patch "filter(\"username\"=='michal')"
                   {:email "nowyMail@aa.bb"}
                   _test-object-spec)
  (serialize-patch "" {:email "nowyMail@aa.bb"} _test-object-spec))

(defn- serialize-delete
  [uriql-query object-data]
  (let [table-name (:name object-data)]
    (-> (build-single-hsql-map :delete table-name uriql-query nil)
        (sql/format))))

(comment
  (serialize-delete "filter(\"username\"=='michal')" _test-object-spec)
  (serialize-delete "sorta(\"name\")" ; should throw exception
                    _test-object-spec)
  (serialize-delete "" _test-object-spec))


(defn hydrate-context
  [ctx serializer-output]
  (if (map? serializer-output)
    (merge ctx serializer-output)
    (assoc ctx :sql-query serializer-output)))


(defn prepare-data-for-serialization
  [ctx path-params body-params app-data method table-names serializer-name]
  (let [objects-data (server-utils/get-associated-objects (app-data :objects)
                                                          table-names)
        _serializer-data (utils/find-first #(= serializer-name (% :name))
                                           (get app-data :serializers))
        serializer-operations (server-utils/get-related-serializer-spec
                                objects-data
                                (_serializer-data :operations)
                                method)
        path-params (normalize-path-parameters ctx
                                               objects-data
                                               serializer-operations
                                               path-params)
        body-params (fields/normalize-body-parameters ctx
                                                      (last objects-data)
                                                      (last
                                                        serializer-operations)
                                                      body-params)]
    [path-params body-params objects-data serializer-operations]))

;; objects-data (server-utils/get-associated-objects (app-data :objects)
;;                                                   tables)
;; normalized-serializer-spec
;;   (map (partial fields/normalize-serializer-spec ctx)
;;     _related-serializer-spec)

(def-interceptor-fn
  serialize-into-model!
  [ctx]
  (let [request-data (get ctx :request)
        request-method (get request-data :request-method)
        [app-name serializer-name tables]
          (server-utils/uri->parsed-info (get request-data :uri) request-method)
        _app-data (server-utils/get-associated-app-data! app-name)
        _path-params (get request-data :path-params)
        _body-params (get request-data :json-params)
        _raw-serializer-data (utils/find-first #(= serializer-name (% :name))
                                               (get _app-data :serializers))
        [path-params body-params objects-data serializer-data]
          (prepare-data-for-serialization ctx
                                          _path-params
                                          _body-params
                                          _app-data
                                          request-method
                                          tables
                                          serializer-name)
        ;; [path-params body-params] (normalize-params ctx
        ;; _related-serializer-spec _path-params _body-params)
       ]
    (->>
      (case request-method
        :post (serialize-post body-params (last objects-data))
        :get (serialize-get path-params serializer-data objects-data)
        :patch
          (serialize-patch (last path-params) body-params (last objects-data))
        :delete (serialize-delete (last path-params) (last objects-data)))
      ;; (hydrate-context ctx)
    )))

(comment
  (_test-context '(serialize-into-model! _test-post-request-map))
  (_test-context '(serialize-into-model! _test-get-request-map))
  (_test-context '(serialize-into-model! _test-patch-request-map))
  (_test-context '(serialize-into-model! _test-delete-request-map)))


(defn fetch-bank-params
  [request-data func-serializer]
  (case (func-serializer :arg-source)
    :query (request-data :query-params)
    :body (request-data :json-params)
    (throw-wolth-exception
      :500
      "Unknown argument source. Available options: :query / :body")))

(comment
  (fetch-bank-params
    (_test-bank-request-map :request)
    {:name "getDate", :arg-source :query, :args [["num" :int]]})
  (fetch-bank-params
    (_test-bank-request-map :request)
    {:name "getDate", :arg-source :body, :args [["num" :int]]}))

#_"Below interceptor fetches and VALIDATES PARAMETERS
   This is the diffrence between model and bank serializer.
   Because the data is not created by the app creator 
   and we do not trust the end user, we have to
   validate the input ourselves
   "
(def-interceptor-fn
  serialize-into-bank
  [ctx]
  (let [request-data (get ctx :request)
        [app-name func-name]
          (server-utils/uri->parsed-info (get request-data :uri) :bank)
        app-data (server-utils/get-associated-app-data! app-name)
        f-serializer (utils/find-first #(= (get % :name) func-name)
                                       (app-data :functions))
        params (fetch-bank-params request-data f-serializer)
        params-normalized (bank-utils/normalize-params params
                                                       (f-serializer :args))]
    (assoc ctx
      :function-data {:function-name func-name,
                      :function-args params-normalized})))

(comment
  (_test-context '(serialize-into-bank _test-bank-request-map)))

(def model-serializer-interceptor
  {:name ::MODEL-SERIALIZER-INTERCEPTOR, :enter serialize-into-model!})

(def bank-serializer-interceptor
  {:name ::BANK-SERIALIZER-INTERCEPTOR, :enter serialize-into-bank})
