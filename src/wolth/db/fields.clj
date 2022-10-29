(ns wolth.db.fields
  (:require [wolth.utils.crypto :refer [create-password-hash]]
            [clojure.string :as str]
            [wolth.utils.common :refer [trim-string]]
            [wolth.db.fields :as fields])
  (:import [java.util UUID]))

(defn create-uuid [& _] (.toString (UUID/randomUUID)))

(comment
  (create-uuid))

(def ^:private normalization-lut
  {:uuid [:str128 create-uuid], :password [:str128 create-password-hash]})

(defn normalize-field
  [f-type value]
  (let [target (normalization-lut f-type)
        [target-type func] target]
    (if (nil? target) (list f-type value) (list target-type (func value)))))

(comment
  (normalize-field :uuid nil)
  (normalize-field :password "haslo")
  (normalize-field :int 111))

(defn get-user-id [ctx] (get-in ctx [:logged-user :id]))

(defn get-user-name [ctx] (get-in ctx [:logged-user :username]))

(def ^:private serializer-normalization-lut
  {:user-id get-user-id, :user-username get-user-name})

(def ^:private serializer-normalization-re
  (->> serializer-normalization-lut
       (keys)
       (map (partial format "<%s>"))
       (str/join "|")
       (re-pattern)))

(defn normalize-serializer-fields
  [ctx fields]
  (mapv (fn [field]
          (if (and (keyword field) (get serializer-normalization-lut field))
            ((get serializer-normalization-lut field) ctx)
            field))
    fields))

(comment
  (normalize-serializer-fields {:logged-user {:id 112}} [:= "author" :user-id]))


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
                 (serializer-normalization-lut it)
                 (it ctx)
                 (format "'%s'" it))]))
       (reduce (fn [acc [pat val]] (str/replace acc pat val)) query)))

(comment
  (normalize-additional-uriql-query
    {:logged-user {:id 221, :username "DominoJachas"}}
    "filter(\"name\"==<:user-username>and\"id\"<><:user-id>)"))


(defn normalize-serializer-spec
  [ctx serializer-spec]
  (let [normalization-fn (partial normalize-serializer-fields ctx)]
    (if (boolean? serializer-spec)
      {}
      (cond-> serializer-spec
        (serializer-spec :additional-query)
          (update :additional-query
                  (partial normalize-additional-uriql-query ctx))
        (serializer-spec :attached) (update :attached normalization-fn)))))

(comment
  (normalize-serializer-spec {:logged-user {:id 221}}
                             {:attached ["nameOfAuthor" :user-username],
                              :additional-query
                                "filter(\"author\"<><:user-id>)"})
  (normalize-serializer-spec {:logged-user {:id 112}} true))