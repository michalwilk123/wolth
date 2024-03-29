(ns wolth.db.user
  (:require [wolth.db.utils :refer [execute-sql-expr!]]
            [io.pedestal.log :as log]
            [next.jdbc :as jdbc]
            [wolth.server.config :refer [def-context cursor-pool]]
            [wolth.server.views :refer [sql-map->map]]
            [honey.sql :as sql]))

(def-context _test-context
             {cursor-pool {"person" (jdbc/get-datasource
                                      {:dbtype "h2", :dbname "mydatabase"})}})

(def user-table
  {:name "User",
   :fields [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
            {:constraints [:not-null :unique], :name "username", :type :str128}
            {:constraints [:not-null], :name "password", :type :password}
            {:constraints [:not-null], :name "role", :type :str128}
            {:name "email", :type :str128}]})

; this table could be implemented fully in h2. Does not need any persistance /
; safety
; User id is only used for deleting / logging out users
(def token-table
  {:name "Token",
   :fields [{:constraints [:not-null :unique], :name "signature", :type :str256}
            {:constraints [:not-null], :name "userId", :type :uuid}],
   :options [:use-h2]})

#_"Default policy:
   - Admin can create any account, update (fe: change password) of any account
   - Admin can read all fields of users excluding their password hash
   - Admin can update any user
   - Admin can delete every account, not including his own :)"
(def user-admin-view
  {:allowed-roles ["admin"],
   :name "user-admin",
   :operations [{:create {:fields ["username" "password" "role" "email"],
                          :attached [["id" :random-uuid]]},
                 :update {:fields ["username" "password" "role" "email"]},
                 :read {:fields ["id" "username" "role" "email"]},
                 :model "User",
                 :delete {:additional-query "filter(\"id\"<><:user-id>)"}}]})

(def user-regular-view
  {:allowed-roles true,
   :name "user-regular",
   :operations [{:create {:fields ["username" "password" "email"],
                          :attached [["role" "regular"] ["id" :random-uuid]]},
                 :update {:fields ["username" "email" "password"],
                          :additional-query "filter(\"id\"<><:user-id>)"},
                 :model "User",
                 :delete {:additional-query "filter(\"id\"<><:user-id>)"}}]})

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
  (_test-context '(fetch-user-data "myAdmin" "person"))
  (_test-context '(fetch-user-data "myAdmina" "person")))

(defn save-auth-token
  [user-id token app-name]
  (log/info ::save-auth-token
            (format "Saving auth token for user: %s" app-name))
  (->> {:insert-into [:Token], :values [{:signature token, :userId user-id}]}
       (sql/format)
       (execute-sql-expr! app-name)))

(comment
  (_test-context
    '(save-auth-token "90692522-3c84-4fe4-9fcd-25b7ebaf828d" "token" "person"))
  (execute-sql-expr! "person" ["DELETE FROM Token;"])
  (execute-sql-expr! "person" ["SELECT * FROM User;"]))

(defn remove-user-tokens
  [user-id app-name]
  (log/info ::remove-user-tokens
            (str "Removing token for user with ID: " user-id))
  (->> {:delete-from :Token, :where [:= :Token.userId user-id]}
       (sql/format)
       (execute-sql-expr! app-name)))

(comment
  (_test-context
    '(save-auth-token "90692522-3c84-4fe4-9fcd-25b7ebaf828d" "TOKEN" "person"))
  (remove-user-tokens "3c2602ea-8771-46cb-bd20-31eb5a8b47e3" "person"))

(defn user-token-exists?
  [token-string app-name]
  (assert (string? token-string))
  (->> {:select :*, :from [:Token], :where [:= :Token.signature token-string]}
       (sql/format)
       (execute-sql-expr! app-name)
       (seq)
       (some?)))

(comment
  (_test-context '(user-token-exists? "token" "person"))
  (_test-context '(user-token-exists? "nie istnieje" "person")))

(defn provide-user-information
  [ctx]
  (log/info ::provide-user-information (get ctx :logged-user))
  (if-let [user-data (get ctx :logged-user)]
    (assoc ctx :response {:status 200, :body user-data})
    (assoc ctx :response {:status 204})))

(def my-user-info-interceptor
  {:name ::MY-USER-INFO-INTERCEPTOR, :enter provide-user-information})
