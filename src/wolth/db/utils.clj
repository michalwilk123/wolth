(ns wolth.db.utils
  (:require [wolth.server.config :refer [cursor-pool]]
            [next.jdbc :refer [execute!]]
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

(comment
  (execute-sql-expr! "person"
                     ["SELECT name, note, id FROM Writer ORDER BY name ASC"])
  (execute-sql-expr!
    "person"
    ["SELECT T1.name, T1.note, T1.id, T2.author, T2.content FROM Writer AS T1 LEFT JOIN Post AS T2 ON T1.id = T2.author ORDER BY T1.name ASC"])
  (execute-sql-expr!
    "todo"
    ["SELECT T1.id, T1.title, T1.authorId, T2.id, T2.todoId, T2.finished, T2.description, T2.name, T2.authorId, T2.creationDate FROM TodoList AS T1 LEFT JOIN TodoItem AS T2 ON T1.id = T2.todoId WHERE (T1.authorId = ?) AND (T1.authorId = ?) ORDER BY T2.finished DESC, T2.creationDate ASC"
     "feba0d79-df8c-4ef1-b830-90400484222a"
     "feba0d79-df8c-4ef1-b830-90400484222a"]))
