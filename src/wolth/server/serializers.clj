(ns wolth.server.serializers
  (:require
    [wolth.utils.common :as utils]
    [wolth.server.exceptions :refer [throw-wolth-exception def-interceptor-fn]]
    [honey.sql :as sql]
    [ring.util.codec :refer [url-decode]]
    [io.pedestal.log :as log]
    [wolth.server.utils :as server-utils]
    [wolth.server.-test-data :refer
     [_test-get-request-map _test-delete-request-map _test-patch-request-map
      _test-post-request-map _serializers_test_app_data _test-object-spec
      _test-normalized-fields _test-json-body _test-bank-request-map]]
    [wolth.server.bank :as bank-utils]
    [wolth.db.uriql :refer
     [build-select merge-select-hsql merge-update-hsql build-update
      build-delete]]
    [wolth.server.config :refer [def-context app-data-container]]
    [wolth.db.fields :as fields]
    [io.pedestal.http.body-params :as body-params]))

(def-context _test-context
             {app-data-container {"test-app" _serializers_test_app_data}})

(defn- normalize-field
  [verbose-field]
  (let [[new-type new-value] (fields/normalize-field (verbose-field :type)
                                                     (verbose-field :data))]
    (merge verbose-field {:type new-type, :data new-value})))


(comment
  (normalize-field {:constraints [:not-null :unique],
                    :name "haslo",
                    :type :password,
                    :data "admin"})
  (normalize-field
    {:constraints [:not-null :unique], :name "id", :type :uuid, :data nil}))

(defn- attatch-field-to-object
  [object-data field]
  (let [object-fields (object-data :fields)
        [key value] field
        related-table (utils/find-first #(= (% :name) (name key))
                                        object-fields)]
    (if (nil? related-table) nil (assoc related-table :data value))))

(comment
  (attatch-field-to-object (first _test-object-spec) [:username "Mariusz"])
  (attatch-field-to-object (first _test-object-spec) [:unknownfield 123]))



(defn- validate-field-by-serializer
  "this is a place to implement custom validation rules"
  [verbose-field]
  verbose-field)

(defn- verbose-field->terse-repr
  [verbose-field]
  (vector (keyword (verbose-field :name)) (verbose-field :data)))

(comment
  (verbose-field->terse-repr {:constraints [:not-null :unique],
                              :name "id",
                              :type :str128,
                              :data "9cf2fae5-8891-4379-a159-5124e2ec6db7"}))

(defn- normalize-field-associeted-w-object
  [params -object-data & {:keys [insert-id], :or {insert-id false}}]
  (let [object-data (first -object-data)]
    (letfn
      [(attatch-optional-uuid-field [verbose-params]
         (if (and insert-id
                  (utils/vector-contains? (object-data :options)
                                          :uuid-identifier))
           (cons {:constraints [:not-null :unique], :name "id", :type :uuid}
                 verbose-params)
           verbose-params))
       (all-fields-found-check [v-fields]
         (if-not (every? some? v-fields)
           (throw-wolth-exception :400
                                  (str "Could not populate all fields: "
                                       v-fields))
           v-fields))]
      (->> params
           (map (partial attatch-field-to-object object-data))
           (all-fields-found-check)
           (attatch-optional-uuid-field)
           (map validate-field-by-serializer)
           (map normalize-field)
           (map verbose-field->terse-repr)
           (into {})))))

(comment
  (normalize-field-associeted-w-object {:username "Mariusz",
                                        :password "haslo",
                                        :email "mariusz@gmail.com",
                                        :role "regular"}
                                       _test-object-spec
                                       :insert-id
                                       true))

(defn- fields->insert-sql
  [table fields]
  (sql/format {:insert-into [(keyword table)], :values [fields]}))

(comment
  (fields->insert-sql "User" _test-normalized-fields))

(defn- serialize-post
  [params serializer-specs object-specs]
  (assert (seq? serializer-specs))
  (assert (map? params))
  (assert (seq? object-specs))
  (let [table-name (:name (first object-specs))
        fields (:fields (first serializer-specs))
        attatched (:attached (first serializer-specs))
        processed-fields (as-> params it
                           (select-keys it (map keyword fields))
                           (utils/assoc-vector it attatched))]
    (if-let [normalized-fields (normalize-field-associeted-w-object
                                 processed-fields
                                 object-specs
                                 :insert-id
                                 true)]
      (fields->insert-sql table-name normalized-fields)
      (throw-wolth-exception :400))))

(comment
  (serialize-post _test-json-body
                  (list {:fields ["username" "email" "password"],
                         :attached [["role" "regular"]]})
                  _test-object-spec))

(defn- serialize-get
  [path-params serializer-specs objects-data]
  (let [serializer-fields (map #(map keyword (% :fields)) serializer-specs)
        filter-subqueries (map :filter serializer-specs)
        object-names (map #(get % :name) objects-data)
        queries (map url-decode (vals path-params))]
    (as-> (utils/zip object-names queries filter-subqueries serializer-fields)
      it
      (map (partial apply build-select) it)
      (merge-select-hsql it)
      (sql/format it))))

(comment
  (serialize-get {:personQuery "<<username"}
                 (list {:fields ["username" "email"],
                        :filter [:= "role" "regular"]})
                 _test-object-spec))


(defn- serialize-patch
  [path-params body-params serializer-specs object-specs]
  (assert (seq? serializer-specs))
  (assert (map? body-params))
  (assert (seq? object-specs))
  (let [object-names (map #(get % :name) object-specs)
        fields (:fields (first serializer-specs))
        attatched (:attatched (first serializer-specs))
        filter-subqueries (map :filter serializer-specs)
        processed-fields (as-> body-params it
                           (select-keys it (map keyword fields))
                           (utils/assoc-vector it attatched))
        queries (map url-decode (vals path-params))]
    (if-let [normalized-fields (normalize-field-associeted-w-object
                                 processed-fields
                                 object-specs)]
      (as-> (utils/zip object-names queries filter-subqueries) it
        (map (partial apply build-update) it)
        (merge-update-hsql it normalized-fields)
        (sql/format it))
      (throw-wolth-exception :400))))

(comment
  (serialize-patch {:personQuery "username==michal"}
                   {:email "nowyMail@aa.bb"}
                   (list {:fields ["username" "email"],
                          :filter [:= "role" "regular"]})
                   _test-object-spec)
  (serialize-patch {:personQuery "<<username"}
                   {:email "nowyMail@aa.bb"}
                   (list {:fields ["username" "email"],
                          :filter [:= "role" "regular"]})
                   _test-object-spec))

(defn- serialize-delete
  [path-params serializer-specs objects-data]
  (let [table-name (:name (first objects-data))
        selector (url-decode (first (vals path-params)))
        filter-subquery (:filter (first serializer-specs))]
    (as-> (list table-name selector filter-subquery) it
      (apply build-delete it)
      (sql/format it))))

(comment
  (serialize-delete {:personQuery "username==michal"}
                    (list {:filter [:= "role" "regular"]})
                    _test-object-spec)
  (serialize-delete {:personQuery "<<username"} ; should throw exception
                    (list {:filter [:= "role" "regular"]})
                    _test-object-spec)
  (serialize-delete {:personQuery "*"}
                    (list {:filter [:< "createdAt" "10-10-2020"]})
                    _test-object-spec))

(def-interceptor-fn
  serialize-into-model
  [ctx]
  (let [request-data (get ctx :request)
        [app-name serializer-name tables] (server-utils/uri->parsed-info
                                            (get request-data :uri)
                                            (get request-data :request-method))
        body-params (get request-data :json-params)
        request-method (get request-data :request-method)
        path-params (get request-data :path-params)
        app-data (server-utils/get-associated-app-data! app-name)
        objects-data (server-utils/get-associated-objects (app-data :objects)
                                                          tables)
        _raw-serializer-data (utils/find-first #(= serializer-name (% :name))
                                               (get app-data :serializers))
        _related-serializer-spec (server-utils/get-related-serializer-spec
                                   objects-data
                                   (_raw-serializer-data :operations)
                                   request-method)
        normalized-serializer-spec
          (map (partial fields/normalize-serializer-spec ctx)
            _related-serializer-spec)]
    (->>
      (case request-method
        :post
          (serialize-post body-params normalized-serializer-spec objects-data)
        :get (serialize-get path-params normalized-serializer-spec objects-data)
        :patch (serialize-patch path-params
                                body-params
                                normalized-serializer-spec
                                objects-data)
        :delete (serialize-delete path-params
                                  normalized-serializer-spec
                                  objects-data))
      (assoc ctx :sql-query))))

(comment
  (_test-context '(serialize-into-model _test-post-request-map))
  (_test-context '(serialize-into-model _test-get-request-map))
  (_test-context '(serialize-into-model _test-patch-request-map))
  (_test-context '(serialize-into-model _test-delete-request-map)))


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
  {:name ::MODEL-SERIALIZER-INTERCEPTOR, :enter serialize-into-model})

(def bank-serializer-interceptor
  {:name ::BANK-SERIALIZER-INTERCEPTOR, :enter serialize-into-bank})
