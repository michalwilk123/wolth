(ns wolth.db.models
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [clojure.string :as str]
            [honey.sql.helpers :as hsql]
            [wolth.db.helpers :refer
             [field-lut translate-keys-to-vals constraints-lut cons-not-nil
              vector-contains? compose]]))


(def db {:dbtype "h2", :dbname "example"})

(def ds (jdbc/get-datasource db))

(def sqlmap {:select [:a :b :c], :from [:foo], :where [:= :foo.a "baz"]})

(def sqlmap2
  {:select :*,
   :from :person,
   :where [:and [:> :person.age "10"] [:= :person.name "Adam"]]})

(def depr-sqlmap3
  {:create-table [:fruit :if-not-exists],
   :with-columns [[:ID :int [:not nil] :auto-increment]
                  [:name [:varchar 32] [:not nil]] [:cost :float :null]
                  [:primary :key (keyword "(id)")]
                  [:foreign :key (keyword "(id)") :references
                   (keyword "Persons(jakiedID)")]]})

;; (jdbc/execute! ds ["
;; create table address (
;;   id int auto_increment primary key,
;;   name varchar(32),
;;   email varchar(255)
;; )"])

(defn execute-honey-map
  [query]
  (assert (map? query))
  (->> query
       (sql/format)
       (jdbc/execute! ds)))


(comment
  (execute-honey-map depr-sqlmap3)
  (jdbc/execute! ds ["TRUNCATE TABLE fruit"])
  (execute-honey-map {:drop-table :fruit}))

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
  [relation]
  (let [ref-field (or (relation :ref) "id")
        fk-name (format "fk_%s_%s" (relation :name) ref-field)
        constraints (cons-not-nil (if (= (relation :ref-type) :o2o) :unique nil)
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
  (expand-single-relation {:name "author",
                           :references "Person",
                           :ref-type :o2m,
                           :constraints [:not-null],
                           :related-name "posts",
                           :ref "username"})
  (expand-single-relation {:name "author",
                           :references "Person",
                           :ref-type :o2o,
                           :related-name "posts"}))

(defn generate-fk-fields
  [relations]
  (assert (or (coll? relations) (nil? relations))
          "Relations must build from the sequence or nil")
  (map expand-single-relation relations))

(comment
  (generate-fk-fields [{:name "author",
                        :ref-type :o2m,
                        :references "Person",
                        :related-name "posts"}
                       {:name "owner",
                        :ref-type :o2o,
                        :references "Person",
                        :related-name "owned-post"}])
  (generate-fk-fields nil))


(defn create-id-field
  [is-uuid & kwargs]
  (if is-uuid
    {:name "id", :type :str32, :constraints [:not-null :primary-key]}
    {:name "id",
     :type :int,
     :constraints [:not-null :primary-key :auto-increment]}))


(defn generate-optional-fields
  [options]
  (let [id-field (create-id-field (vector-contains? options :uuid-identifier))]
    (map build-single-table-field (list id-field))))

(comment
  (generate-optional-fields [:uuid-identifier])
  (generate-optional-fields []))

(defn generate-create-table-fields
  [fields]
  (assert (vector? fields) "Fields should be an instance of vector")
  (map build-single-table-field fields))

(comment
  (generate-create-table-fields [{:name "content", :type :text}]))

(defn generate-create-table-query
  [object-data]
  (let [additional-fields (generate-optional-fields (object-data :options))
        regular-fields (generate-create-table-fields (object-data :fields))
        fk-fields (generate-fk-fields (object-data :relations))]
    (format "CREATE TABLE IF NOT EXISTS %s (%s);"
            (object-data :name)
            (str/join ", "
                      (flatten
                        (list additional-fields regular-fields fk-fields))))))

(def _test-object-data-1
  {:fields [{:name "name", :type :text}
            {:name "age", :type :int, :constraints [:not-null]}],
   :name "Person",
   :options [:uuid-identifier]})

(def _test-object-data-2
  {:fields [{:name "content", :type :text}],
   :name "Post",
   :relations [{:name "author",
                :ref-type :o2m,
                :references "Person",
                :related-name "posts"}]})

(def _test-object-data-3
  {:name "Worker",
   :fields [{:name "first-name", :type :str128} {:name "email", :type :str128}],
   :options [:uuid-identifier],
   :relations [{:name "supervisor",
                :ref-type :o2m,
                :related-name "subordinates",
                :references "Worker"}]})

(def _test-objects (list (generate-create-table-query _test-object-data-2)))

(comment
  (generate-create-table-query _test-object-data-1)
  (generate-create-table-query _test-object-data-2)
  (generate-create-table-query _test-object-data-3)
  (jdbc/execute! ds (vector "DROP TABLE Person;"))
  (jdbc/execute! ds (list (generate-create-table-query _test-object-data-2))))

(defn cross-field-w-related-table
  [table field]
  (letfn [(assign-correct-id-type [field]
            (if (and (table :options)
                     (vector-contains? (table :options) :uuid-identifier))
              (assoc field :type :uuid)
              (assoc field :type :int)))]
    (if (= (field :references) (table :name))
      (compose field assign-correct-id-type)
      field)))

(comment
  (cross-field-w-related-table
    {:fields [{:name "note", :type :str32}], :name "Person"}
    {:name "author",
     :ref-type :o2m,
     :references "Person",
     :related-name "posts"})
  (cross-field-w-related-table {:fields [{:name "note", :type :str32}],
                                :name "Person",
                                :options [:uuid-identifier]}
                               {:name "author",
                                :ref-type :o2m,
                                :references "Person",
                                :related-name "posts"}))

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
  (map (fn [tab] (reduce cross-two-tables tab tables)) tables))


(comment
  (cross-together-table-data [_test-object-data-1 _test-object-data-2])
  (cross-together-table-data [_test-object-data-3]))

(defn create-all-tables
  [objects-data]
  (->> objects-data
       (cross-together-table-data)
       (map generate-create-table-query)
       (map list)
       (map (partial jdbc/execute! ds)))
  )


(comment
  (create-all-tables [_test-object-data-1 _test-object-data-2])
  (create-all-tables [ _test-object-data-1])
  )

(comment
  (sql/format sqlmap)
  (sql/format depr-sqlmap3)
  (sql/format {:drop-table :foo})
  (hsql/create-table :foo :if-not-exists :with-columns [:id :int]))
