(ns wolth.db.models
  (:require [honey.sql :as sql]
            [clojure.string :as str]
            [honey.sql.helpers :as hsql]
            [wolth.utils.common :refer
             [field-lut translate-keys-to-vals constraints-lut cons-not-nil
              find-first]]))


(defn build-single-table-field
  [field]
  (str/join " "
            (concat (list (field :name) (field-lut (field :type)))
                    (translate-keys-to-vals (or (field :constraints) (list))
                                            constraints-lut))))

(comment
  (build-single-table-field
    {:name "username", :type :uuid, :constraints [:not-null :unique]}))

(defn expand-single-relation
  [parent-table-name relation]
  (let [ref-field (or (relation :ref) "id")
        fk-name (format "fk_%s_%s" (relation :name) parent-table-name)
        constraints (cons-not-nil
                      (if (= (relation :relation-type) :o2o) :unique nil)
                      (relation :constraints))
        formatted-field (build-single-table-field
                          {:name (relation :name),
                           :type (or (relation :type) :int),
                           :constraints constraints})]
    (list formatted-field
          (format "CONSTRAINT %s FOREIGN KEY(%s) REFERENCES %s(%s)"
                  fk-name
                  (relation :name)
                  (relation :references)
                  ref-field))))

(comment
  (expand-single-relation "Post"
                          {:name "author",
                           :references "Person",
                           :relation-type :o2m,
                           :constraints [:not-null],
                           :ref "username"})
  (expand-single-relation
    "Post"
    {:name "author", :references "Person", :relation-type :o2o}))

(defn generate-fk-fields
  [parent-table-name relations]
  (assert (or (coll? relations) (nil? relations))
          "Relations must build from the sequence or nil")
  (map (partial expand-single-relation parent-table-name) relations))

(comment
  (generate-fk-fields
    "Post"
    [{:name "author", :relation-type :o2m, :references "Person"}
     {:name "owner", :relation-type :o2o, :references "Person"}])
  (generate-fk-fields "Post" nil))


(defn generate-create-table-fields
  [fields]
  (assert (vector? fields) "Fields should be an instance of vector")
  (map build-single-table-field fields))

(comment
  (generate-create-table-fields [{:name "content", :type :text}]))

(defn generate-create-table-query
  [object-data]
  (let [regular-fields (generate-create-table-fields (object-data :fields))
        fk-fields (generate-fk-fields (object-data :name)
                                      (object-data :relations))]
    (format "CREATE TABLE IF NOT EXISTS %s (%s);"
            (object-data :name)
            (str/join ", " (flatten (list regular-fields fk-fields))))))

(def _test-object-data-1
  {:fields [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
            {:name "name", :type :text}
            {:name "age", :type :int, :constraints [:not-null]}],
   :name "Person"})

(def _test-object-data-2
  {:fields [{:name "id", :type :id, :constraints [:id-constraints]}
            {:name "content", :type :text}],
   :name "Post",
   :relations [{:name "author", :relation-type :o2m, :references "Person"}]})

(def _test-object-data-3
  {:name "Worker",
   :fields [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
            {:name "id", :type :id, :constraints [:id-constraints]}
            {:name "first-name", :type :str128} {:name "email", :type :str128}],
   :relations
     [{:name "supervisor", :relation-type :o2m, :references "Worker"}]})

(def _test-objects (list (generate-create-table-query _test-object-data-2)))

(comment
  (generate-create-table-query _test-object-data-1)
  (generate-create-table-query _test-object-data-2)
  (generate-create-table-query _test-object-data-3))

(defn cross-field-w-related-table
  [table field]
  (update
    field
    :type
    (fn [value]
      (or
        value
        (when (= (field :references) (table :name))
          (let
            [id-field (find-first (fn [it] (= (it :name) "id")) (table :fields))
             _ (assert
                 (some? id-field)
                 "The object that is the target of the relationship, MUST include the id field")]
            (get id-field :type)))))))

(comment
  (cross-field-w-related-table
    {:fields [{:name "id", :type :id, :constraints [:id-constraints]}
              {:name "note", :type :str32}],
     :name "Person"}
    {:name "author", :relation-type :o2m, :references "Person"})
  (cross-field-w-related-table
    {:fields [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
              {:name "note", :type :str32}],
     :name "Person"}
    {:name "author", :relation-type :o2m, :references "Person"}))

(defn cross-two-tables
  [tab tab-to-fetch-info]
  (let [modified-relations
          (as-> tab it
            (get it :relations)
            (map (partial cross-field-w-related-table tab-to-fetch-info) it))]
    (assoc tab :relations modified-relations)))

(comment
  (cross-two-tables _test-object-data-2 _test-object-data-1))

(defn cross-together-table-data
  "Some tables rely on other tables. In this function we populate 
   all additional fields not passed directly"
  [tables]
  (assert (vector? tables)
          "function accept as an argument an list of table objects")
  (for [table tables] (reduce cross-two-tables table tables)))


(comment
  (cross-together-table-data [_test-object-data-1 _test-object-data-2])
  (cross-together-table-data [_test-object-data-3]))

(defn generate-create-table-sql
  [objects-data]
  (assert (sequential? objects-data))
  (->> objects-data
       (cross-together-table-data)
       (map generate-create-table-query)
       (map list)))

(comment
  (generate-create-table-sql [_test-object-data-1 _test-object-data-2])
  (generate-create-table-sql [_test-object-data-1]))

(comment
  (sql/format {:drop-table :foo})
  (hsql/create-table :foo :if-not-exists :with-columns [:id :int]))
