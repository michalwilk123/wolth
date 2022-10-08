(ns wolth.db.utils
  (:require [wolth.server.config :refer [cursor-pool]]
            [next.jdbc :refer [get-datasource execute!]]
            [wolth.server.exceptions :refer [throw-wolth-exception]]))

(defn get-datasource-from-memory
  [app-name]
  (cond (nil? @cursor-pool)
          (throw-wolth-exception :500 "Data sources not initialized")
        (nil? (@cursor-pool app-name))
          (throw-wolth-exception
            :400
            (str
              "Bad request! tried to fetch data from unexisting application: "
              app-name))
        :else (@cursor-pool app-name)))


(defn execute-sql-expr!
  [app-name sql-expr]
  (execute! (get-datasource-from-memory app-name) sql-expr))