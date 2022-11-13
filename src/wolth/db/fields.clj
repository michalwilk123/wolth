(ns wolth.db.fields
  (:require [wolth.utils.crypto :refer [create-password-hash]]
            [clojure.string :as str]
            [honey.sql :as sql]
            [wolth.server.exceptions :refer [throw-wolth-exception]]
            [wolth.utils.common :as common]
            [wolth.server.-test-data :refer
             [_test-normalized-fields _test-object-spec
              _test-app-data-w-relations _test-serializer-spec]]
            [wolth.db.fields :as fields]))

(def ^:private key-normalization-lut {:password [:str128 create-password-hash]})

(def value-normalization-lut
  {:random-uuid common/create-uuid,
   :today-date common/today-date,
   :user-id common/get-user-id,
   :null (fn [_] nil),
   :user-username common/get-user-name})

(defn normalize-value-field
  [ctx value]
  (if-let [norm-func (value-normalization-lut value)]
    (norm-func ctx)
    value))

(comment
  (normalize-value-field {} :random-uuid)
  (normalize-value-field {} :today-date)
  (normalize-value-field {:logged-user {:id 112}} :user-id)
  (normalize-value-field {:logged-user {:id 112}} :null)
  (normalize-value-field {:logged-user {:username "marik1234"}} :user-username)
  (normalize-value-field {} "jakies dane"))

(defn normalize-key-field
  [f-type value]
  (let [target (key-normalization-lut f-type)
        [target-type func] target]
    (if (nil? target) (list f-type value) (list target-type (func value)))))

(comment
  (normalize-key-field :uuid nil)
  (normalize-key-field :password "haslo")
  (normalize-key-field :int 111))

(def ^:private serializer-normalization-re
  (->> value-normalization-lut
       (keys)
       (map (partial format "<%s>"))
       (str/join "|")
       (re-pattern)))

#_"(13.11.2022) TODO: there is complex bug going on. So basically 
   with the way we doin the uriql-query hydration. 
   We just restricted everything to only use the
   string in additional-query field. The explantion here is simple,
   you unfortunatly cannot concatenate native java objects to string reliably.
   (Of course you can use some wierd hax, but it is just bad design to do so)
   This would mean passing this in diffrent way, which would open up
   many possibilities for even more complex function kwords.
   But unfortunatly, it will take some time to implement which i do not
   posses right now"
(defn normalize-additional-uriql-query
  [ctx query]
  (->> query
       (re-seq serializer-normalization-re)
       (set)
       (map (fn [val]
              [(re-pattern val)
               (as-> val it
                 (str it)
                 (common/trim-string it :start 2)
                 (keyword it)
                 (value-normalization-lut it)
                 (it ctx)
                 (format "'%s'" it))]))
       (reduce (fn [acc [pat val]] (str/replace acc pat val)) query)))

(comment
  (normalize-additional-uriql-query
    {:logged-user {:id 221, :username "DominoJachas"}}
    "filter(\"name\"==<:user-username>and\"id\"<><:user-id>)")
  (normalize-additional-uriql-query {} "filter(\"author\"==<:null>)"))


(defn normalize-serializer-spec
  [ctx serializer-spec]
  (if (boolean? serializer-spec)
    {}
    (cond-> serializer-spec
      (serializer-spec :additional-query)
        (update :additional-query
                (partial normalize-additional-uriql-query ctx)))))

(comment
  (normalize-serializer-spec {:logged-user {:id 221}}
                             {:attached ["nameOfAuthor" :user-username],
                              :additional-query
                                "filter(\"author\"<><:user-id>)"})
  (normalize-serializer-spec {:logged-user {:id 112}} true))

(defn- normalize-field
  [ctx verbose-field]
  (let [value (normalize-value-field ctx (verbose-field :data))
        [new-type value] (normalize-key-field (verbose-field :type) value)]
    (merge verbose-field {:type new-type, :data value})))


(comment
  (normalize-field {}
                   {:constraints [:not-null :unique],
                    :name "haslo",
                    :type :password,
                    :data "admin"})
  (normalize-field {}
                   {:constraints [:not-null],
                    :name "timestamp",
                    :type :date-tz,
                    :data :today-date})
  (normalize-field
    {:logged-user {:id 997}}
    {:constraints [:not-null], :name "authorId", :type :id, :data :user-id})
  (normalize-field {}
                   {:constraints [:uuid-constraints],
                    :name "id",
                    :type :uuid,
                    :data :random-uuid}))

(defn- attatch-field-to-object
  [object-data field]
  (let [object-fields (concat (get object-data :relations)
                              (object-data :fields))
        [key value] field
        related-table (common/find-first #(= (% :name) (name key))
                                         object-fields)]
    (if (nil? related-table) nil (assoc related-table :data value))))

(comment
  (attatch-field-to-object _test-object-spec [:username "Mariusz"])
  (attatch-field-to-object (second (_test-app-data-w-relations :objects))
                           [:country_id 123])
  (attatch-field-to-object _test-object-spec [:unknownfield 123]))



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

(defn associate-fields-w-object
  [ctx params object-data]
  (letfn [(all-fields-found-check [v-fields]
            (if-not (every? some? v-fields)
              (throw-wolth-exception :400
                                     (str "Could not populate all fields: "
                                          (vec v-fields)))
              v-fields))]
    (->> params
         (map (partial attatch-field-to-object object-data))
         (all-fields-found-check)
         (map validate-field-by-serializer)
         (map (partial normalize-field ctx))
         (map verbose-field->terse-repr)
         (into {}))))

(comment
  (associate-fields-w-object {:logged-user {:id 123, :username "lalala"}}
                             {:id :random-uuid,
                              :username :user-username,
                              :password "haslo",
                              :email "mariusz@gmail.com",
                              :role "regular"}
                             _test-object-spec)
  (associate-fields-w-object {}
                             {:id :random-uuid,
                              :username "Mariusz",
                              :password "haslo",
                              :email "mariusz@gmail.com",
                              :role "regular"}
                             _test-object-spec))


(defn- fields->insert-sql
  [table fields]
  (sql/format {:insert-into [(keyword table)], :values [fields]}))

(comment
  (fields->insert-sql "User" _test-normalized-fields))

(defn normalize-attatched-field
  [ctx field]
  (if (-> field
          (second)
          (keyword?))
    (if-let [norm-fn (value-normalization-lut (second field))]
      (assoc field 1 (norm-fn ctx))
      (throw-wolth-exception :500
                             (str "Tried to call unknown function: "
                                  (second field))))
    field))

(comment
  (normalize-attatched-field {} ["name" "Michał"])
  (normalize-attatched-field {:logged-user {:id 112, :username "aaa"}}
                             ["id" :user-id])
  (normalize-attatched-field {} ["id" :random-uuid])
  (normalize-attatched-field {} ["createdAt" :today-date])
  (normalize-attatched-field {} ["createdAt" :unknown-function]))


(defn normalize-body-parameters
  [ctx object-data serializer-operation body-params]
  (if (true? serializer-operation)
    body-params
    (let [attached-fields (get serializer-operation :attached)
          attached-fields (map (partial normalize-attatched-field ctx)
                            attached-fields)
          selected-fields (as-> serializer-operation it
                            (get it :fields)
                            (map keyword it)
                            (select-keys body-params it)
                            (common/assoc-vector it attached-fields))]
      (associate-fields-w-object ctx selected-fields object-data))))

(comment
  (normalize-body-parameters
    {:logged-user {:id 111}}
    _test-object-spec
    (get-in _test-serializer-spec [:operations 0 :create])
    {:aa 111, :password "lalala", :email "lalala@lala.la", :username "lala"})
  (normalize-body-parameters
    {}
    _test-object-spec
    true
    {:password "lalala", :email "lalala@lala.la", :username "lala"}))
