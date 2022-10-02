(ns wolth.server.resolvers
  (:require [wolth.server.exceptions :refer [throw-wolth-exception]]
            [next.jdbc :as jdbc]))

(def _test_serializer_data
  {:json-params
     {:username "Mariusz", :password "haslo", :email "mariusz@gmail.com"},
   :protocol "HTTP/1.1",
   :async-supported? true,
   :remote-addr "127.0.0.1",
   :headers {"accept" "*/*",
             "user-agent" "Thunder Client (https://www.thunderclient.com)",
             "connection" "close",
             "host" "localhost:8002",
             "accept-encoding" "gzip, deflate, br",
             "content-length" "20",
             "content-type" "application/json"},
   :server-port 8002,
   :content-length 20,
   :content-type "application/json",
   :path-info "/app/User/public",
   :character-encoding "UTF-8",
   :uri "/app/User/public",
   :server-name "localhost",
   :sql-query
     ["INSERT INTO User (id, username, password, email, role) VALUES (?, ?, ?, ?, ?)"
      "ab6b029c-f98b-4b5d-b7a0-faa9a1d83db3" "Mariusz"
      "100$12$argon2id$v13$hgN/r4flg4x904r398B9kg$QSAXLGtxKeZ8s9eyYdK5FTNyREW9SirLclEFAa8WhIc$$$"
      "mariusz@gmail.com" "regular"],
   :app-name "app",
   :query-string nil,
   :path-params {},
   :scheme :http,
   :request-method :post,
   :context-path ""})

(def cursor-pool {"app" (jdbc/get-datasource {:dbtype "h2", :dbname "app"})})

(defn resolve-model-query
  [ctx]
  (if-not (contains? ctx :sql-query)
    (throw-wolth-exception :500 "No SQL query found in context!")
    (let [query (ctx :sql-query)
          result (jdbc/execute! (cursor-pool (ctx :app-name)) query)]
      (as-> ctx it (dissoc it :sql-query) (assoc it :result result)))))

(comment
  (resolve-model-query _test_serializer_data))

(def model-resolver-interceptor
  {:name ::MODEL-RESOLVER-INTERCEPTOR, :enter resolve-model-query})