(ns wolth.utils.misc)

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