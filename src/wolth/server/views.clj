(ns wolth.server.views
  (:require [wolth.server.exceptions :refer [def-interceptor-fn]]))

(def-interceptor-fn
  view-interceptor-fn
  [ctx]
  (assoc ctx
    :response {:status 200,
               :body (str "Success!!"
                          (select-keys ctx
                                       [:response :exception-occured :sql-query
                                        :result]))}))

(def view-interceptor
  {:name ::WOLTH-VIEW-INTERCEPTOR, :enter view-interceptor-fn})

