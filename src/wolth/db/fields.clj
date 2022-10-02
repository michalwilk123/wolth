(ns wolth.db.fields
  (:require [wolth.utils.crypto :refer [create-password-hash]])
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
