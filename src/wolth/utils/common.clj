(ns wolth.utils.common
  (:require [clojure.string :as str])
  (:import [java.util UUID]))


(def field-lut
  {:char "VARCHAR(1)",
   :str8 "VARCHAR(8)",
   :str16 "VARCHAR(16)",
   :str32 "VARCHAR(32)",
   :str128 "VARCHAR(128)",
   :str256 "VARCHAR(256)",
   :str2048 "VARCHAR(2048)",
   :uuid "VARCHAR(48)",
   :password "VARCHAR(128)",
   :bool "BOOLEAN",
   :text "TEXT",
   :int "INTEGER",
   :id "INTEGER",
   :float "FLOAT",
   :double "DOUBLE",
   :date-tz "TIMESTAMP WITH TIME ZONE"})

(def constraints-lut
  {:not-null "NOT NULL",
   :unique "UNIQUE",
   :primary-key "PRIMARY KEY",
   :identity "IDENTITY",
   :auto-increment "AUTO_INCREMENT",
   :id-constraints "NOT NULL PRIMARY KEY AUTO_INCREMENT",
   :uuid-constraints "NOT NULL PRIMARY KEY "})

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

(defn tee [func dat] (doall (func dat)) dat)

(comment
  (tee (fn [x] (println x)) "lalalala"))

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
  (cons-not-nil nil nil)
  (cons-not-nil nil '(1 2 3))
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


(defn zip [& colls] (partition (count colls) (apply interleave colls)))

(comment
  (zip (range 10) (repeat 10 "AAA")))

(defn get-first-matching-pred [preds] (some (fn [func] (func)) preds))

(comment
  (get-first-matching-pred [(partial identity false) (partial identity 10)
                            (partial even? 122)]))


; source:
; https://michaelwhatcott.com/generating-random-alphanumeric-codes-in-clojure/
(defn- char-range [lo hi] (range (int lo) (inc (int hi))))

(def ^:private alpha-numeric
  (map char (concat (char-range \a \z) (char-range \A \Z) (char-range \0 \9))))

(defn rand-string
  [n]
  (apply str (take n (repeatedly #(rand-nth alpha-numeric)))))

(comment
  (rand-string 10))


(defn multiple-get [in-map keys] ((apply juxt keys) in-map))

(comment
  (multiple-get {:aaa 123, :bbb 999, :ccc 1000, :ddd 1001}
                [:bbb :ccc :aaa :ddd])
  (multiple-get {:aaa 123, :bbb 999, :ccc 1000, :ddd 1001} [:ddd :bbb]))


(defn concatv
  [vecs]
  (let [coll (apply concat vecs)] (if (not-empty coll) (vec coll) nil)))

(comment
  (concatv (list [1 2 3 4] [11 222 33 44 55]))
  (concatv (list [1 2 3 4] nil))
  (concatv (list nil nil)))

(defn concat-vec-field-in-maps
  [map-objs kw]
  (->> map-objs
       (map (fn [el] (get el kw)))
       (map (fn [item]
              (if (or (sequential? item) (nil? item)) item (list item))))
       (apply concat)
       ((fn [it] (if (not-empty it) (vec it) nil)))))

(comment
  (concat-vec-field-in-maps (list {:uu [1122 33],
                                   :abc ["aaa" "bb" "cc" "dd" "ee"]}
                                  {:abc [1 2 3 4 5], :pp 123}
                                  {:mm 33})
                            :abc)
  (concat-vec-field-in-maps
    (list {:uu [1122 33], :abc :lalala} {:abc [1 2 3 4 5], :pp 123} {:mm 33})
    :abca))


(defn seqs-equal?
  [& seqs]
  (and (apply = (map count seqs)) (every? true? (apply (partial map =) seqs))))

(comment
  (seqs-equal? (range 8) (range 10))
  (seqs-equal? (range 10) (range 10))
  (seqs-equal? (repeat 10 1) (range 10)))

(defn trim-string
  [text & {:keys [start], :or {start 1}}]
  (subs text start (dec (.length text))))

(comment
  (trim-string "-dsads-")
  (trim-string "-12345678-" :start 2))

(defn remove-nil-vals-from-map
  [m]
  (->> m
       (remove (comp nil? second))
       (into {})))

(comment
  (remove-nil-vals-from-map {:dsadsa 321, :popop nil, :ooo "dsadsa"}))

(defn clob-to-string
  [obj]
  (let [parsed-clob (-> obj
                        (.toString)
                        (str/replace-first #"clob[0-9]+: " "")
                        (trim-string))]
    (.free obj)
    parsed-clob)) ;;TODO: this is very hacky and should be improved for sure

(defn create-uuid [& _] (.toString (UUID/randomUUID)))
(defn today-date [& _] (java.time.LocalDateTime/now))
(defn get-user-id [ctx] (get-in ctx [:logged-user :id]))
(defn get-user-name [ctx] (get-in ctx [:logged-user :username]))

(comment
  (create-uuid))
