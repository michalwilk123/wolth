(ns wolth.db.user)


(def user-table
  {:fields [{:constraints [:not-null], :name "username", :type :str128}
            {:constraints [:not-null], :name "password", :type :str128}
            {:constraints [:not-null], :name "email", :type :str128}],
   :options [:uuid-identifier]})

; this table could be implemented fully in h2. Does not need any persistance /
; safety
; User id is only used for deleting / logging out users
(def token-table
  {:fields [{:constraints [:not-null], :name "signature", :type :str256}
            {:constraints [:not-null], :name "user", :type :uuid}],
   :options [:use-h2]})

(defn get-user-auth-data
  [username db]
  {:password
     "100$12$argon2id$v13$qjVHAso9NbIZz1alruEjjg$pobQt1Ij/vk6gmhf1yOCGwNFwoxQzRgQuSFmXBjkg9Y$$$",
   :role "admin",
   :username username})
