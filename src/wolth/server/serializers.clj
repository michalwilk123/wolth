(ns wolth.server.serializers
  (:require [wolth.utils.common :as utils]
            [wolth.server.exceptions :refer [throw-wolth-exception]]
            [wolth.server.utils :as server-utils]
            [honey.sql :as sql]
            [wolth.db.fields :as fields]))

(def _test-serializer-spec
  {:name "public",
   :allowed-roles true,
   :operations [{:model "User",
                 :read {:fields ["author" "content" "id"], :attached []},
                 :update {:fields ["username" "email"]},
                 :create {:fields ["username" "email" "password"],
                          :attached [["role" "regular"]]},
                 :delete true}]})

(def _test-object-spec
  '({:name "User",
     :fields
       [{:constraints [:not-null :unique], :name "username", :type :str128}
        {:constraints [:not-null], :name "password", :type :password}
        {:constraints [:not-null], :name "role", :type :str128}
        {:constraints [:unique], :name "email", :type :str128}],
     :options [:uuid-identifier]}))

(def ^:private _test-normalized-fields
  {:id "65ebc5a7-348c-4bb7-a58b-54d96a1b41bf",
   :username "Mariusz",
   :password
     "100$12$argon2id$v13$qen5BpBkZOs7qT9abjb9iA$eq9Fr5Im3Nqx35GHDIMg7ADbyq09zvuoV+sbvNlYYWI$$$",
   :email "mariusz@gmail.com",
   :role "regular"})

(def _test-json-body
  {:username "Mariusz", :password "haslo", :email "mariusz@gmail.com"})

(def _test-request-map
  {:json-params _test-json-body,
   :protocol "HTTP/1.1",
   :async-supported? true,
   :remote-addr "127.0.0.1",
   :headers {"accept" "*/*",
             "user-agent" "Thunder Client (https://www.thunderclient.com)",
             "connection" "close",
             "host" "localhost:8002",
             "accept-encoding" "gzip, deflate, br",
             "content-length" "20",
             "content-type" "application/json"},
   :server-port 8002,
   :content-length 20,
   :content-type "application/json",
   :path-info "/app/User/public",
   :character-encoding "UTF-8",
   :uri "/app/User/public",
   :server-name "localhost",
   :query-string nil,
   :path-params {},
   :scheme :http,
   :request-method :post,
   :context-path ""})

(defn normalize-field
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

(defn attatch-field-to-object
  [object-data field]
  (let [object-fields (object-data :fields)
        [key value] field
        related-table (first (filter #(= (% :name) (name key)) object-fields))]
    (if (nil? related-table) nil (assoc related-table :data value))))

(comment
  (attatch-field-to-object (first _test-object-spec) [:username "Mariusz"])
  (attatch-field-to-object (first _test-object-spec) [:unknownfield 123]))



(defn validate-field-by-serializer
  "this is a place to implement custom validation rules"
  [verbose-field]
  verbose-field)

(defn verbose-field->terse-repr
  [verbose-field]
  (vector (keyword (verbose-field :name)) (verbose-field :data)))

(comment
  (verbose-field->terse-repr {:constraints [:not-null :unique],
                              :name "id",
                              :type :str128,
                              :data "9cf2fae5-8891-4379-a159-5124e2ec6db7"}))

(defn normalize-field-associeted-w-object
  [params -object-data]
  (let [object-data (first -object-data)]
    (letfn
      [(attatch-optional-uuid-field [verbose-params]
         (if (utils/vector-contains? (object-data :options) :uuid-identifier)
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
                                       _test-object-spec))

(defn fields->sql
  [table fields]
  (sql/format {:insert-into [(keyword table)], :values [fields]}))

(comment
  (fields->sql "User" _test-normalized-fields))

(defn serialize-post
  [params spec object-spec]
  (assert (map? spec))
  (assert (map? params))
  (assert (seq? object-spec))
  (let [table-name (:name (first object-spec))
        related-model (first (filter #(= table-name (% :model))
                               (spec :operations)))
        fields (get-in related-model (list :create :fields))
        attatched (get-in related-model (list :create :attached))
        processed-fields (as-> params it
                           (utils/sift-keys-in-map it fields)
                           (utils/assoc-vector it attatched))]
    (if-let [normalized-fields (normalize-field-associeted-w-object
                                 processed-fields
                                 object-spec)]
      (fields->sql table-name normalized-fields)
      (throw-wolth-exception :400))))

(comment
  (serialize-post _test-json-body _test-serializer-spec _test-object-spec))

(defn serialize-into-model
  [ctx]
  (let [[app-name serializer-name tables]
          (server-utils/uri->parsed-info (ctx :uri) (ctx :request-method))
        body-params (ctx :json-params)
        path-params (ctx :path-params)
        app-data (server-utils/get-associated-app-data! app-name)
        objects-data (server-utils/get-associated-objects app-data tables)
        serializer-data (server-utils/get-serializer-data app-data
                                                          serializer-name)]
    (->> (case (ctx :request-method)
           :post (serialize-post body-params serializer-data objects-data)
           :get (println "MAM geta"))
         (assoc ctx :sql-query))))

(comment
  (serialize-into-model _test-request-map))


(defn serialize-into-bank [ctx] ctx)

(def model-serializer-interceptor
  {:name ::MODEL-SERIALIZER-INTERCEPTOR, :enter serialize-into-model})

(def bank-serializer-interceptor
  {:name ::BANK-SERIALIZER-INTERCEPTOR, :enter 'serialize-into-bank})