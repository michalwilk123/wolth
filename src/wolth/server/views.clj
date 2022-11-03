(ns wolth.server.views
  (:require [wolth.server.exceptions :refer [def-interceptor-fn]]
            [clojure.spec.alpha :as s]
            [wolth.utils.common :refer [sql-map->map create-nested-sql-result]]))


(defn create-model-view
  [ctx]
  (let [result (get ctx :model-result)
        join-fields (get ctx :relation-fields)
        table-names (get ctx :table-names)
        request-method (get-in ctx [:request :request-method])
        response (case request-method
                   :get {:status 200,
                         :body (create-nested-sql-result result table-names join-fields)}
                   :post {:status 201, :body {:message "Created data"}}
                   :patch {:status 200, :body {:message "Updated data"}}
                   :delete {:status 200, :body {:message "Deleted data"}})]
    (assoc ctx :response response)))

(s/def ::body
  (s/or :a map?
        :b vector?
        :c number?
        :d string?
        :e nil?))

(s/def ::status (s/int-in 100 600))

(s/def ::result-object (s/and map? (s/keys :req-un [::body ::status])))

(comment
  (s/valid? ::result-object {:status 200, :body "OK"})
  (s/valid? ::result-object "OKEJ")
  (s/explain ::result-object "OKEJ")
  (s/valid? ::result-object {:status 1000, :body (fn [x] x)})
  (s/explain ::result-object {:status 1000, :body (fn [x] x)}))

(defn create-bank-view
  [ctx]
  (let [result (ctx :bank-result)]
    (if (s/valid? ::result-object result)
      (assoc ctx :response result)
      (assoc ctx :response {:status 200, :body result}))))

(def-interceptor-fn view-interceptor-fn
                    [ctx]
                    (cond (get ctx :bank-result) (create-bank-view ctx)
                          (get ctx :model-result) (create-model-view ctx)
                          :else (assoc ctx :response {:status 204, :body nil})))


(def wolth-view-interceptor
  {:name ::VIEW-INTERCEPTOR, :enter view-interceptor-fn})


