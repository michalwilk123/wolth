(ns wolth.utils.common
  (:require [clojure.string :as str]
            [wolth.db.utils :refer [execute-sql-expr!]]
            [wolth.utils.misc :refer
             [unflat-end-condition unflat-nested-struct]]
            [io.pedestal.log :as log])
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

(defn translate-to-readable-form
  [val]
  (if (instance? java.sql.Clob val) (clob-to-string val) val))

(comment
  (translate-to-readable-form 1)
  (->> (execute-sql-expr!
         "person"
         ["SELECT name, note, id FROM Writer ORDER BY name ASC"])
       (first)
       (:WRITER/NOTE)
       (translate-to-readable-form))
  (->> (execute-sql-expr! "person" ["SELECT AUTHOR, CONTENT FROM Post"])))

(defn sql-map->map
  [nmap]
  (log/info ::sql-map->map nmap)
  (into {}
        (map (fn [[k val]]
               [(-> k
                    (name)
                    (str/split #"/")
                    (last)
                    (str/lower-case)
                    (keyword)) (translate-to-readable-form val)])
          nmap)))


(comment
  (sql-map->map #:USER{:ID "440b753d-1928-4007-bcd5-392ef5b3d0e7",
                 :USERNAME "admin",
                 :PASSWORD "haslo",
                 :ROLE "admin",
                 :EMAIL nil}))

(defn parse-multitable-sql-result
  [sql-map]
  (letfn [(get-tab-name [key]
            (-> key
                (str)
                (str/split #"\/")
                (first)
                (subs 1)))]
    (reduce
      (fn [acc [k value]]
        (update acc
                (get-tab-name k)
                (fn [m]
                  (assoc m
                    (-> k
                        (name)
                        (str/lower-case)
                        (keyword))
                      (translate-to-readable-form value)))))
      {}
      sql-map)))

(comment
  (parse-multitable-sql-result {:WRITER/NAME "writer3",
                                :WRITER/NOTE "Testowa notatka",
                                :WRITER/ID 3,
                                :POST/ID 3,
                                :POST/AUTHOR 3,
                                :POST/CONTENT "pierwszy post"}))

(defn squash-maps
  [maps table-names]
  (unflat-nested-struct maps table-names unflat-end-condition))

(comment
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}}
                {"tab1" {:aa 2}, "tab2" {:dd "gg"}}]
               (list "tab1" "tab2"))
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}}
                {"tab1" {:aa nil}, "tab2" {:dd "gg"}}]
               (list "tab1" "tab2"))
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}, "tab3" {:qq 1}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}, "tab3" {:qq 2}}
                {"tab1" {:aa 2}, "tab2" {:dd "gg"}, "tab3" {:qq 3}}]
               (list "tab1" "tab2" "tab3"))
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}, "tab3" {:qq 1}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}, "tab3" {:qq nil}}
                {"tab1" {:aa 2}, "tab2" {:dd "cc"}, "tab3" {:qq nil}}
                {"tab1" {:aa 3}, "tab2" {:dd nil}, "tab3" {:qq nil}}]
               (list "tab1" "tab2" "tab3"))
  (squash-maps [{"tab1" {:aa 1}} {"tab1" {:aa 2}} {"tab1" {:aa 2}}]
               (list "tab1")))

(defn join-queries-with-fields
  [query-structure fields-to-inject]
  (if (empty? fields-to-inject)
    query-structure
    (reduce (fn [acc [k value]]
              (cons (assoc k
                      (first fields-to-inject) (join-queries-with-fields
                                                 value
                                                 (rest fields-to-inject)))
                    acc))
      nil
      query-structure)))

(comment
  (join-queries-with-fields '{{:aa 1} ({:dd "bb"}),
                              {:aa 2} ({:dd "gg"} {:dd "aa"})}
                            (list "tab1-items"))
  (join-queries-with-fields '{{:aa 1} {{:dd "bb"} ({:qq 1})},
                              {:aa 2} {{:dd "gg"} ({:qq 3}),
                                       {:dd "aa"} ({:qq 2})}}
                            (list "tab1-items" "tab2-items")))

(defn empty-relation?
  [sql-result]
  (some? (find-first (fn [[k val]]
                       (and (-> k
                                (name)
                                (= "ID"))
                            (nil? val)))
                     sql-result)))

(comment
  (empty-relation? {:WRITER/NAME "writer3",
                    :WRITER/NOTE "Testowa notatka",
                    :WRITER/ID 3,
                    :POST/ID 3,
                    :POST/AUTHOR 3,
                    :POST/CONTENT "pierwszy post"})
  (empty-relation? {:WRITER/NAME "writer3",
                    :WRITER/NOTE "Testowa notatka",
                    :WRITER/ID nil,
                    :POST/ID 3,
                    :POST/AUTHOR 3,
                    :POST/CONTENT "pierwszy post"}))

(defn create-nested-sql-result
  [parsed-result tables relation-fields]
  (let [uppercased-table-names (map str/upper-case tables)]
    (as-> parsed-result it
      (map parse-multitable-sql-result it)
      (squash-maps it uppercased-table-names)
      (join-queries-with-fields it relation-fields))))

(comment
  (create-nested-sql-result [{:WRITER/NAME "writer3",
                              :WRITER/NOTE "Testowa notatka",
                              :WRITER/ID 3,
                              :POST/ID 3,
                              :POST/AUTHOR 3,
                              :POST/CONTENT "pierwszy post"}
                             {:WRITER/NAME "writer2",
                              :WRITER/NOTE "notatka 123",
                              :WRITER/ID 3,
                              :POST/ID nil,
                              :POST/AUTHOR nil,
                              :POST/CONTENT nil}
                             {:WRITER/NAME "writer1",
                              :WRITER/NOTE "lorem ipsum",
                              :WRITER/ID 3,
                              :POST/ID 2,
                              :POST/AUTHOR 3,
                              :POST/CONTENT "drugi post"}]
                            (list "Writer" "Post")
                            (list "posts")))

(comment
  (sql-map->map #:USER{:ID "440b753d-1928-4007-bcd5-392ef5b3d0e7",
                 :USERNAME "admin",
                 :PASSWORD "haslo",
                 :ROLE "admin",
                 :EMAIL nil}))

(defn create-uuid [& _] (.toString (UUID/randomUUID)))
(defn today-date [& _] (java.time.LocalDateTime/now))
(defn get-user-id [ctx] (get-in ctx [:logged-user :id]))
(defn get-user-name [ctx] (get-in ctx [:logged-user :username]))

(comment
  (create-uuid))
