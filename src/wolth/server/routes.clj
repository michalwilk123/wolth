(ns wolth.server.routes
  (:require [honey.sql.helpers :as sql-help]
            [honey.sql :as sql]
            [io.pedestal.interceptor.helpers :refer [defhandler]]
            [io.pedestal.http.route :as route]))

(defn respond-hello
  [request]          ;; <1>
  {:status 200, :body "siemano mdna,sds,a"}) ;; <2>

(defn resp-or [req] {:status 200, :body "siemano mdna,sds,a"}) ;; <2>

;; (def routes #{["/greet" :get respond-hello :route-name :greet]})

;; (def routes [[:hello-world :http
;;               ["/order" {:get 'resp-or}
;;                ["/:id" {:get 'respond-hello}]]]])

(def routes '[[:hello ["/hello-world" {:get hello-world}]]])

(defn hello-world [req] {:status 200, :body "EJSDNJDSANK Worlddjdjd"})

(defn example [req] {:status 200, :body "JESTEM ENDPOINTEM"})

;; (defhandler hello
;;             [req]
;;             {:status 200, :body (str "HNALDER!!" (req :path-params))})

(defn hello [req] {:status 200, :body (str "HNALDER!!" (req :path-params))})

; IMPORTANT! Expand route must be outside the init function
(def route-table
  (route/expand-routes
    [[:hello ["/hello-world" {:get 'hello-world} ["/:id" {:get hello}]]]]))


(comment
  route-table)

(comment
  (sql/format (hsql/create-table :foo :if-not-exists)))