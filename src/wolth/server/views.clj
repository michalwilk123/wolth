(ns wolth.server.views
  (:require [wolth.server.exceptions :refer [def-interceptor-fn]]
            [wolth.utils.common :refer [sql-map->map]]))

(def-interceptor-fn
  model-view-interceptor-fn
  [ctx]
  (if-let [result (get ctx :result)]
    (assoc ctx
      :response (case (get-in ctx [:request :request-method])
                  :get {:status 200,
                        :body (if (coll? result)
                                (map sql-map->map result)
                                (sql-map->map result))}
                  :post {:status 201, :body {:message "Created data"}}
                  :patch {:status 200, :body {:message "Updated data"}}
                  :delete {:status 200, :body {:message "Deleted data"}}))
    (assoc ctx
      :response {:status 200,
                 :body "No result from resolver, but request was successful"})))

(def-interceptor-fn function-view-interceptor-fn
                    [ctx]
                    (if (get ctx :result)
                      (assoc ctx
                        :response {:status 200, :body (get ctx :result)})
                      (assoc ctx :response {:status 204})))

(def model-view-interceptor
  {:name ::WOLTH-VIEW-INTERCEPTOR, :enter model-view-interceptor-fn})

(def function-view-interceptor
  {:name ::WOLTH-VIEW-INTERCEPTOR, :enter function-view-interceptor-fn})

