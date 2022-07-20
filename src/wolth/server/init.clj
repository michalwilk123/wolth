(ns wolth.server.init
  (:gen-class)
  (:require [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [ring.util.response :as ring-resp]))

(def common-interceptors [(body-params/body-params) http/json-body])

(def base-empty-route
  #{["/" :get
     (conj common-interceptors
           (fn [r]
             (ring-resp/response {:hello "hello this is base page of wolth"})))
     :route-name :base-wolh-route]})

(def base-server-config
  {::http/join? false, ;; block thread
   ::http/routes #(route/expand-routes (deref #'base-empty-route)),
   ::http/type :jetty,
   ::http/port 8001,
   ::http/host "localhost",
   ::http/resource-path "/public",
   ::http/container-options {:h2c? true, :h2? false, :ssl? false},
   ::http/allowed-origins {:creds true, :allowed-origins (constantly true)},
   ::http/secure-headers {:content-security-policy-settings {:object-src
                                                               "'none'"}}})


(def production-server-config {::http/host "0.0.0.0"})

(defn build-server
  [user-config]
  (-> base-server-config
      (merge user-config
             (if (get user-config :prod) production-server-config nil))
      http/default-interceptors
      http/dev-interceptors
      http/create-server
      http/start))

(comment
  (build-server {}))
