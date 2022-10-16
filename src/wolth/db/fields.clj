(ns wolth.db.fields
  (:require [wolth.utils.crypto :refer [create-password-hash]]
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

(defn normalize-serializer-fields
  [ctx fields]
  (vec (map (fn [field]
              (if (and (keyword field) (get serializer-normalization-lut field))
                ((get serializer-normalization-lut field) ctx)
                field))
         fields)))

(comment
  (normalize-serializer-fields {:logged-user {:id 112}} [:= "author" :user-id]))

(defn normalize-serializer-spec
  [ctx serializer-spec]
  (let [normalization-fn (partial normalize-serializer-fields ctx)]
    (if (boolean? serializer-spec)
      {}
      (cond-> serializer-spec
        (serializer-spec :filter) (update-in [:filter] normalization-fn)
        (serializer-spec :attached) (update-in [:attached] normalization-fn)))))

(comment
  (normalize-serializer-spec {:logged-user {:id 221}}
                             {:attached ["nameOfAuthor" :user-username],
                              :filter [:<> "author" :user-id]})
  (normalize-serializer-spec {:logged-user {:id 112}} true))