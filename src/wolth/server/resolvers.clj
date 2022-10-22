(ns wolth.server.resolvers
  (:require [wolth.server.exceptions :refer
             [throw-wolth-exception def-interceptor-fn]]
            [wolth.server.config :refer [def-context cursor-pool]]
            [wolth.server.-test-data :refer [_resolver_serializer_test_data]]
            [io.pedestal.log :as log]
            [wolth.db.utils :refer [execute-sql-expr!]]
            [next.jdbc :as jdbc]))



(def-context _test-context
             {cursor-pool {"app" (jdbc/get-datasource {:dbtype "h2",
                                                       :dbname "testing"})}})
(def-interceptor-fn
  resolve-model-query
  [ctx]
  (log/info ::resolve-model-query (select-keys ctx [:sql-query]))
  (if-not (contains? ctx :sql-query)
    (throw-wolth-exception :500 "No SQL query found in context!")
    (let [query (ctx :sql-query)
          result (execute-sql-expr! (get ctx :app-name) query)]
      (as-> ctx it (dissoc it :sql-query) (assoc it :result result)))))

(comment
  (_test-context '(resolve-model-query _resolver_serializer_test_data)))

(def model-resolver-interceptor
  {:name ::MODEL-RESOLVER-INTERCEPTOR, :enter resolve-model-query})

(defn create-database-driver [])

(def function-resolver-interceptor
  {:name ::MODEL-RESOLVER-INTERCEPTOR, :enter resolve-model-query})

