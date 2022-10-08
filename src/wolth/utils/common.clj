(ns wolth.utils.common
  (:require [clojure.string :as str]))

(def field-lut
  {:char "VARCHAR(1)",
   :str8 "VARCHAR(8)",
   :str16 "VARCHAR(16)",
   :str32 "VARCHAR(32)",
   :str128 "VARCHAR(128)",
   :str256 "VARCHAR(256)",
   :uuid "VARCHAR(48)",
   :password "VARCHAR(128)",
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

; USE H2 is experimental feature (not implemented yet)
(def available-table-options [:uuid-identifier :use-h2])

(def availiable-relationships [:o2m :o2o])

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

(defn sift-keys-in-map
  [in-map keys]
  (assert (vector? keys))
  (into {} (filter #(vector-contains? keys (name (first %))) in-map)))

(comment
  (sift-keys-in-map {:aaa 123, :bbb 222} ["aaa" "bbb"])
  (sift-keys-in-map {:aaa 123, :bbb 222} ["bbb"])
  (sift-keys-in-map {:aaa 123, :bbb 222} nil))

(defn assoc-vector
  [in-map vec]
  (reduce (fn [acc val] (assoc acc (keyword (first val)) (second val)))
    in-map
    vec))

(comment
  (assoc-vector {:aaa 123, :bbb 111} [["ccc" 333] ["bbb" 999]])
  (assoc-vector {:aaa 123, :bbb 111} [["ccc" 333] ["ddd" 999]]))

(defn sql-map->map
  [nmap]
  (into {}
        (map (fn [[k val]]
               [(-> k
                    (name)
                    (str/split #"/")
                    (last)
                    (str/lower-case)
                    (keyword)) val])
          nmap)))


(comment
  (sql-map->map #:USER{:ID "440b753d-1928-4007-bcd5-392ef5b3d0e7",
                 :USERNAME "admin",
                 :PASSWORD "haslo",
                 :ROLE "admin",
                 :EMAIL nil}))

(defn zip [& colls] (partition (count colls) (apply interleave colls)))

(comment
  (zip (range 10) (repeat 10 "AAA")))
