(ns wolth.server.bank
  (:require [wolth.server.exceptions :refer [throw-wolth-exception]]
            [wolth.utils.common :refer [compose]]))


(defn create-reg-matcher
  [reg-expr]
  (fn [cand]
    (or (->> cand
             (re-matches reg-expr))
        :INVALID-INPUT)))

(def ^:private validators
  {:int (create-reg-matcher #"([1-9]+[0-9]*)|0"),
   :string (constantly true),
   :float (create-reg-matcher #"(([1-9]+[0-9]*)|0)(\.[0-9]*)?")})

; https://stackoverflow.com/questions/35199808
(def ^:private normalizers
  {:int #(Integer/parseInt %), :string identity, :float #(Float/parseFloat %)})

;; (map (fn [spec-item] ( conj spec-item (get params (name (first spec-item )
;; )))) it)))

(defn- validation-successful?
  [val]
  (if-not (= val :INVALID-INPUT)
    val
    (throw-wolth-exception :400 "Bad query parameters for function")))

(defn normalize-params
  [params spec]
  (as-> spec it
    (map (fn fetch-param-values [spec-item]
           (->> spec-item
                (first)
                (keyword)
                (get params)
                ((fn [val] (if ( nil? val) (throw-wolth-exception :400 "Some required parameters were not assigned") val)))
                (conj spec-item)))
      it)
    (map (fn normalize-value [ext-spec]
           (let [f-type (second ext-spec)
                 value (last ext-spec)]
             (validation-successful? ( validators f-type value ))
             ((normalizers f-type) value)
             ))
      it)))

(comment
  (normalize-params {:text "dsadsa", :num "123"}
                    [["num" :int] ["text" :string]])
  (normalize-params {:num "123" :text "dsadsa" }
                    [["num" :int] ["text" :string]])
  (normalize-params {:text "dsadsa", :num "dsadsa"}
                    [["num" :int] ["text" :string]])
  (normalize-params {:text "dsadsa"}
                    [["num" :int] ["text" :string]])
  )
