(ns wolth.db.utils
  (:require [wolth.server.config :refer [cursor-pool]]
            [wolth.server.exceptions :refer [throw-wolth-exception]]))

(defn get-data-source
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

