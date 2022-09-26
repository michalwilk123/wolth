(ns start                               ;; <1>
  (:require [io.pedestal.http :as http] ;; <2>
            [io.pedestal.http.route :as route]
            [wolth.server.routes :as r])) ;; <3>

(defn respond-hello
  [request]          ;; <1>
  {:status 200, :body "Hello, world!"}) ;; <2>

;; (def routes
;;   (route/expand-routes                                   ;; <1>
;;    #{["/greet" :get respond-hello :route-name :greet]})) ;; <2>



(def initial
  {:env :dev,
   ;; do not block thread that starts web server
   ::http/join? false,
   ::http/type :jetty,
   ::http/port 8002,
   ::http/routes (fn [] (deref #'r/route-table)),
   ::http/resource-path "/public",
   ::http/container-options {:h2c? true, :h2? false, :ssl? false},
   ::http/allowed-origins {:creds true, :allowed-origins (constantly true)},
   ::http/secure-headers {:content-security-policy-settings {:object-src
                                                               "'none'"}}})

(defonce server-instance (atom initial))

(defn create-server [] (swap! server-instance http/create-server))

(defn start-server [] (http/start @server-instance))

(defn stop-server [] (http/stop @server-instance))

(comment
  (create-server)
  ;; (reset! server-instance)
  (@server-instance)
  (reset! server-instance initial)
  (start-server)
  (stop-server)
  (route/expand-routes (deref #'r/route-table-v2))
  (route/expand-routes r/route-table-v2)
  (r/route-table-v2))

