(ns wolth.utils.spec
  (:require [clojure.spec.alpha :as s]
            [wolth.utils.common :as help]))


(s/def ::name string?)
(s/def ::type (partial contains? help/field-lut))
(s/def ::constaints
  (s/and vector? (s/coll-of (partial contains? help/constraints-lut))))
(s/def ::ref-type (partial help/vector-contains? help/availiable-relationships))
(s/def ::references string?)
(s/def ::related-name string?)


(s/def ::wolth-table-field
  (s/keys :req-un [::name ::type] :opt-un [::constaints]))
(s/def ::wolth-table-relation
  (s/keys :req-un [::name ::ref-type ::references] :opt-un [::related-name]))

(s/def ::fields (s/and vector? (s/coll-of ::wolth-table-field)))
(s/def ::relations (s/and vector? (s/coll-of ::wolth-table-relation)))
(s/def ::options
  (s/and vector?
         (s/coll-of (partial help/vector-contains?
                             help/available-table-options))))


(s/def ::wolth-object
  (s/keys :req-un [::fields ::name] :opt-un [::relations ::options]))

(s/def ::objects (s/and vector? (s/coll-of ::wolth-object)))

(def aaa
  [{:fields [{:constaints [:not-null], :name "name", :type :str32}
             {:name "note", :type :text}],
    :name "Person",
    :options [:uuid-identifier]}
   {:fields [{:name "content", :type :text}],
    :name "Post",
    :options [:uuid-identifier],
    :relations [{:name "author",
                 :ref-type :o2m,
                 :references "Person",
                 :related-name "posts"}]}])

(comment
  (s/explain ::objects aaa) ; this is for debug / user messages
  (s/valid? ::objects aaa) ; this is for validation (outputs boolean)
)
