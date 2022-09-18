(ns wolth.db.helpers)

(def field-lut
  {:char "VARCHAR(1)",
   :str8 "VARCHAR(8)",
   :str16 "VARCHAR(16)",
   :str32 "VARCHAR(32)",
   :str128 "VARCHAR(128)",
   :uuid "VARCHAR(32)",
   :bool "BOOLEAN",
   :text "TEXT",
   :int "INTEGER",
   :float "FLOAT",
   :double "DOUBLE",
   :date "TIMESTAMPZ"})

(def constraints-lut
  {:not-null "NOT NULL",
   :unique "UNIQUE",
   :primary-key "PRIMARY KEY",
   :identity "IDENTITY",
   :auto-increment "AUTO_INCREMENT"})


(defn create-relationship-field-name [fieldset-1 fieldset-2] nil)

(defn create-o2m-relation [] nil)

(defn create-o2o-relation [] nil)

(def relationship-map {:o2m create-o2m-relation, :o2o create-o2o-relation})

(defn relationship?
  [field-vec]
  (assert (keyword? field-vec))
  (contains? relationship-map field-vec))


(comment
  (relationship? :o2o)
  (relationship? :abc))

(defn translate-keys-to-vals
  [keys src-map]
  (let [translated (map (fn [k] (src-map k)) keys)]
    (if (some nil? translated)
      (throw (RuntimeException.
               (format "Cannot find some of the keys %s in the options map: %s"
                       keys
                       src-map)))
      translated)))

(comment
  (translate-keys-to-vals [:not-null] constraints-lut)
  (translate-keys-to-vals [:not-null :foo] constraints-lut))

(defn vector-contains?
  [vec needle]
  ; i want to cast nil to boolean so i use some and some? simultaneously
  (some? (some #(= needle %) vec)))

(comment
  (vector-contains? ["cc" "aa" "bb"] "aa")
  (vector-contains? ["cc" "aa" "bb"] 1))

(defn find-first [pred coll] (first (filter pred coll)))

(comment
  (find-first even? [1 1 11 21 22 24 20])
  (find-first even? [1 1 11 99 91 9 3]))

(defn cons-not-nil [item coll] (if (some? item) (cons item coll) coll))

(comment
  (cons-not-nil 11 [1 2 3 4])
  (cons-not-nil 11 nil)
  (cons-not-nil nil [1 2 3 4]))


(defn compose [data & funcs] (reduce (fn [data func] (func data)) data funcs))

(comment
  (compose 1 (partial + 3) str)
  (compose 1 (partial + 3) (partial * 2) (partial + -1)))