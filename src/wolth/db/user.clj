(ns wolth.db.user
  (:require [wolth.db.models :as models]))


(def user-table
  {:name "User",
   :fields [{:constraints [:not-null :unique], :name "username", :type :str128}
            {:constraints [:not-null], :name "password", :type :password}
            {:constraints [:not-null], :name "role", :type :str128}
            {:constraints [:unique], :name "email", :type :str128}],
   :options [:uuid-identifier]})

; this table could be implemented fully in h2. Does not need any persistance /
; safety
; User id is only used for deleting / logging out users
(def token-table
  {:name "Token",
   :fields [{:constraints [:not-null], :name "signature", :type :str256}
            {:constraints [:not-null], :name "userId", :type :uuid}],
   :options [:use-h2]})

;; deprecated!
(def user-admin-view {:admin [{:actions "rcud", :fields "*", :table "User"}]})

(defn get-user-auth-data
  [username db]
  {:password
     "100$12$argon2id$v13$qjVHAso9NbIZz1alruEjjg$pobQt1Ij/vk6gmhf1yOCGwNFwoxQzRgQuSFmXBjkg9Y$$$",
   :role "admin",
   :username username})



(comment
  (models/create-all-tables [token-table user-table]))


(def insert-q
  {:insert-into [:User], :values [{:username "Tytus", :email "Bomba"}]})