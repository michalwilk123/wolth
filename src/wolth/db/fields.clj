(ns wolth.db.fields
  (:require [wolth.utils.crypto :refer [create-password-hash]]
            [clojure.string :as str]
            [honey.sql :as sql]
            [wolth.server.exceptions :refer [throw-wolth-exception]]
            [wolth.utils.common :refer [trim-string find-first]]
            [wolth.server.-test-data :refer
             [_test-normalized-fields _test-object-spec
              _test-app-data-w-relations]]
            [wolth.db.fields :as fields])
  (:import [java.util UUID]))

(defn create-uuid [& _] (.toString (UUID/randomUUID)))
(defn today-date [& _] (java.time.LocalDateTime/now))
(defn get-user-id [ctx] (get-in ctx [:logged-user :id]))
(defn get-user-name [ctx] (get-in ctx [:logged-user :username]))

(comment
  (create-uuid))

(def ^:private key-normalization-lut {:password [:str128 create-password-hash]})

(def ^:private value-normalization-lut
  {:random-uuid create-uuid,
   :today-date today-date,
   :user-id get-user-id,
   :user-username get-user-name})

(defn normalize-value-field
  [ctx value]
  (if-let [norm-func (value-normalization-lut value)]
    (norm-func ctx)
    value))

(comment
  (normalize-value-field {} :random-uuid)
  (normalize-value-field {} :today-date)
  (normalize-value-field {:logged-user {:id 112}} :user-id)
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

(defn normalize-additional-uriql-query
  [ctx query]
  (->> query
       (re-seq serializer-normalization-re)
       (set)
       (map (fn [val]
              [(re-pattern val)
               (as-> val it
                 (str it)
                 (trim-string it :start 2)
                 (keyword it)
                 (value-normalization-lut it)
                 (it ctx)
                 (format "'%s'" it))]))
       (reduce (fn [acc [pat val]] (str/replace acc pat val)) query)))

(comment
  (normalize-additional-uriql-query
    {:logged-user {:id 221, :username "DominoJachas"}}
    "filter(\"name\"==<:user-username>and\"id\"<><:user-id>)"))


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
        related-table (find-first #(= (% :name) (name key)) object-fields)]
    (if (nil? related-table) nil (assoc related-table :data value))))

(comment
  (attatch-field-to-object (first _test-object-spec) [:username "Mariusz"])
  (attatch-field-to-object (second (_test-app-data-w-relations :objects))
                           [:country_id 123])
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

(defn normalize-field-associeted-w-object
  [ctx params -object-data]
  (let [object-data (first -object-data)]
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
           (into {})))))

(comment
  (normalize-field-associeted-w-object
    {:logged-user {:id 123, :username "lalala"}}
    {:id :random-uuid,
     :username :user-username,
     :password "haslo",
     :email "mariusz@gmail.com",
     :role "regular"}
    _test-object-spec)
  (normalize-field-associeted-w-object {}
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

;; (defn normalize-attached-fields
;;   [ctx fields]
;;   (mapv (fn [field]
;;           (if (and (keyword field) (get serializer-normalization-lut field))
;;             ((get serializer-normalization-lut field) ctx)
;;             field))
;;     fields))

;; (comment
;;   (normalize-attached-fields {:logged-user {:id 112}} [ [:= "author"
;;   :user-id] ["lalal" "aaaa"] ["num" 123] ["qqqq" :user-username]]))

           ;; (attatch-optional-uuid-field)
      ;; [(attatch-optional-uuid-field [verbose-params]
      ;;    (if (and insert-id
      ;;             (utils/vector-contains? (object-data :options)
      ;;                                     :uuid-identifier))
      ;;      (cons {:constraints [:not-null :unique], :name "id", :type :uuid}
      ;;            verbose-params)
      ;;      verbose-params))
  ;; (let [normalization-fn (partial normalize-attached-fields ctx)]
        ;; (serializer-spec :attached) (update :attached normalization-fn)