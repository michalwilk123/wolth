(ns wolth.db.user
  (:require [wolth.db.models :as models]
            [wolth.db.utils :refer [execute-sql-expr!]]
            [io.pedestal.log :as log]
            [wolth.utils.common :refer [sql-map->map]]
            [honey.sql :as sql]))


(def user-table
  {:name "User",
   :fields [{:constraints [:not-null :unique], :name "username", :type :str128}
            {:constraints [:not-null], :name "password", :type :password}
            {:constraints [:not-null], :name "role", :type :str128}
            {:name "email", :type :str128}],
   :options [:uuid-identifier]})

; this table could be implemented fully in h2. Does not need any persistance /
; safety
; User id is only used for deleting / logging out users
(def token-table
  {:name "Token",
   :fields [{:constraints [:not-null :unique], :name "signature", :type :str256}
            {:constraints [:not-null], :name "userId", :type :uuid}],
   :options [:use-h2]})

(def user-admin-view
  {:allowed-roles ["admin"],
   :name "user-admin",
   :operations [{:create {:fields ["username" "password" "role" "email"]},
                 :update {:fields ["username" "password" "role" "email"]},
                 :read {:fields ["id" "username" "role" "email"]},
                 :model "User",
                 :delete true}]})

(def user-regular-view
  {:allowed-roles true,
   :name "user-regular",
   :operations [{:create {:fields ["username" "password" "email"],
                          :attached [["role" "regular"]]},
                 :update {:fields ["username" "email"]},
                 :model "User",
                 :delete true}]})

(defn format-sql-response [sql-resp])

(comment
  (format-sql-response [#:USER{:ID "fcb25ade-b382-4d0d-9b69-773b0918db30",
                         :USERNAME "myAdmin",
                         :PASSWORD "aaaa",
                         :ROLE "admin",
                         :EMAIL nil}]))

(defn fetch-user-data
  [username app-name]
  (let [fetched
          (->> {:select :*, :from [:User], :where [:= :User.username username]}
               (sql/format)
               (execute-sql-expr! app-name)
               (first)
               (sql-map->map))]
    (if-not (empty? fetched) fetched nil)))

(comment
  (fetch-user-data "myAdmin" "person")
  (fetch-user-data "myAdmina" "person"))


(comment
  (models/generate-create-table-sql [token-table user-table]))

(defn save-auth-token
  [user-id token app-name]
  (log/info ::save-auth-token
            (format "Saving auth token for user: %s" app-name))
  (->> {:insert-into [:Token], :values [{:signature token, :userId user-id}]}
       (sql/format)
       (execute-sql-expr! app-name)))

(comment
  (save-auth-token "90692522-3c84-4fe4-9fcd-25b7ebaf828d" "TOKEN" "person")
  (execute-sql-expr! "person" ["DELETE FROM Token;"])
  (execute-sql-expr! "person" ["SELECT * FROM User;"]))
