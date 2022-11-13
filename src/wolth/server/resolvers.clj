(ns wolth.server.resolvers
  (:require [wolth.server.exceptions :refer [def-interceptor-fn]]
            [wolth.server.config :refer [def-context cursor-pool]]
            [wolth.utils.common :refer [multiple-get]]
            [wolth.utils.loader :refer
             [load-app-functions! create-namespace-name]]
            [wolth.server.-test-data :refer
             [_resolver_model_ctx_test_data _resolver_func_ctx_test_data
              _resolver_test_function_data _resolver_func_test_namespace]]
            [io.pedestal.log :as log]
            [wolth.db.utils :refer [execute-sql-expr!]]
            [next.jdbc :as jdbc]))



(def-context _test-context
             {cursor-pool {"app" (jdbc/get-datasource {:dbtype "h2",
                                                       :dbname "testing"})}})


(defn- resolve-model
  [ctx sql-query]
  (let [result (execute-sql-expr! (get ctx :app-name) sql-query)]
    (log/info ::resolve-model result)
    (as-> ctx it (dissoc it :sql-query) (assoc it :model-result result))))

(defn- resolve-bank
  [ctx func-data]
  (log/info ::resolve-bank (str "Resolving function: " func-data))
  (let [app-name (ctx :app-name)
        [func-name f-args] (multiple-get func-data
                                         (list :function-name :function-args))
        function-path (->> func-name
                           (str (create-namespace-name app-name) "/")
                           (symbol))]
    (assert list? f-args)
    (assoc ctx :bank-result (apply (eval function-path) f-args))))

(comment
  (load-app-functions! _resolver_func_test_namespace
                       _resolver_test_function_data)
  (resolve-bank {:app-name "test-app"}
                {:function-name "getDate", :function-args (list 997)})
  (resolve-bank {:app-name "person"}
                {:function-name "getPrimes", :function-args (list 997)}))

(def-interceptor-fn resolve-query
                    [ctx]
                    (log/info ::resolve-model-query
                              (select-keys ctx [:sql-query :function-data]))
                    (let [sql-query (get ctx :sql-query)
                          func-data (get ctx :function-data)]
                      (cond (some? sql-query) (resolve-model ctx sql-query)
                            (some? func-data) (resolve-bank ctx func-data))))

(comment
  (_test-context '(resolve-query _resolver_model_ctx_test_data))
  (_test-context '(resolve-query _resolver_func_ctx_test_data)))

(def wolth-resolver-interceptor
  {:name ::RESOLVER-INTERCEPTOR, :enter resolve-query})

(defn create-database-driver [])

