(ns wolth.utils.misc
  (:require [wolth.utils.common :refer [tee]]))

(defn get-relevant-relations
  [joins chain]
  (let [relations (filter (fn [x] (= (first x) (last chain))) joins)]
    (map rest relations)))

(comment
  (get-relevant-relations '((:A :B) (:B D)) '(:A :C :D :A))
  (get-relevant-relations '((:A :B) (:B D) (:A :D)) '(:A :C :D :A))
  (get-relevant-relations '((:A :B) (:B D) (:A :D)) '(:A :C :D :F)))

(defn append-to-chain [chain rels] (map (fn [it] (concat chain it)) rels))

(comment
  (append-to-chain '(:A :C :D :F) '((:B) (:C) (:Q))))

(defn create-chain
  [items joins]
  (->> items
       (map (partial get-relevant-relations joins))
       (map append-to-chain items)
       (remove empty?)
       (apply concat)))

(comment
  (create-chain '((:A) (:B) (:C)) '((:A :B)))
  (create-chain '((:A) (:B) (:C)) '((:A :B) (:B :A)))
  (create-chain '((:A :B) (:B :A)) '((:A :B) (:B :A)))
  (create-chain '() '((:A :B) (:B :A)))
  (create-chain '((:A :C) (:B :D) (:C :A)) '((:A :B)))
  (create-chain '(("Country") ("City"))
                '(("Country" "City") ("City" "Country")))
  (create-chain '(("Country" "City") ("City" "Country"))
                '(("Country" "City") ("City" "Country"))))

(defn generate-full-model-chain
  [items joins depth]
  (reduce (fn [acc _] (concat items (create-chain acc joins)))
    items
    (range depth)))

(comment
  (generate-full-model-chain '((:A :B) (:B :A)) '((:A :B) (:B :A)) 1)
  (generate-full-model-chain '((:A) (:B)) '((:A :B) (:B :A)) 1)
  (generate-full-model-chain '((:A) (:B)) '((:A :B) (:B :A)) 3)
  (generate-full-model-chain '(("Country") ("City"))
                             '(("Country" "City") ("City" "Country"))
                             2))

(defn unflat-end-condition
  [maps next-name]
  (and (-> maps
           (count)
           (= 1))
       (as-> maps it (first it) (get it next-name) (vals it) (every? nil? it))))

(comment
  (unflat-end-condition [{:aa {:a 1, :b 2}, :bb {:c 1, :d 1}}
                         {:aa {:a 1, :b 2}, :bb {:c 1, :d 1}}]
                        :bb)
  (unflat-end-condition [{:aa {:a 1, :b 2}, :bb {:c 1, :d 1}}] :bb)
  (unflat-end-condition [{:aa {:a 1, :b 2}, :bb {:c nil, :d nil}}] :bb))

(defn unflat-nested-struct
  [structs path end-condition?]
  (let [relevant-tab (first path)
        next-paths (rest path)]
    (if (empty? next-paths)
      (map (fn [x] (get x relevant-tab)) structs)
      (as-> structs it
        (reduce (fn [acc item]
                  (let [curr-k (get item relevant-tab)
                        next-val (dissoc item relevant-tab)
                        current (get acc curr-k '())]
                    (assoc acc curr-k (cons next-val current))))
          {}
          it)
        (for [[k value] it]
          (vector k
                  (if (end-condition? value (first next-paths))
                    (list)
                    (unflat-nested-struct value next-paths end-condition?))))
        (into {} it)))))

(comment
  (unflat-nested-struct [{"a" 1, "b" 2} {"a" 1, "b" 2} {"a" 3, "b" 2}
                         {"a" 2, "b" 4} {"a" 1, "b" 4} {"a" 1, "b" 9}
                         {"a" 2, "b" 4} {"a" 4, "b" 0}]
                        ["a" "b"]
                        (fn [ms name] (= (get (first ms) name) 0)))
  (unflat-nested-struct [{"a" 1, "b" 2} {"a" 1, "b" 2} {"a" 3, "b" 2}
                         {"a" 2, "b" 4} {"a" 1, "b" 4} {"a" 1, "b" 9}
                         {"a" 2, "b" 4} {"a" 4, "b" 0}]
                        ["a"]
                        (fn [ms name] (= (get (first ms) name) 0))))