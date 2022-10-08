(ns wolth.start                               ;; <1>
  (:gen-class)
  (:require [io.pedestal.http :as http] ;; <2>
            [io.pedestal.http.route :as route]
            [wolth.server.utils :refer [app-path->app-name]]
            [wolth.utils.loader :refer
             [load-application! test-application-file! load-user-functionalities
              store-applications! store-db-connections! store-routes!
              create-sql-tables! create-admin-account!]]
            [wolth.server.routes :as r])) ;; <3>

(defn respond-hello
  [request]          ;; <1>
  {:status 200, :body "Hello, world!"}) ;; <2>


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

(defn create-server [] (doall (swap! server-instance http/create-server)))

(defn start-server [] (http/start @server-instance))

(defn stop-server [] (http/stop @server-instance))

(defn -main
  [& app-paths]
  (run! test-application-file! app-paths)
  (let [applications (->> app-paths
                          (map load-application!)
                          (map load-user-functionalities))
        app-names (map app-path->app-name app-paths)
        generated-routes (r/generate-full-routes app-names applications)]
    (store-applications! app-names applications)
    (store-db-connections! app-names applications)
    (create-sql-tables! app-names applications)
    (map (partial apply create-admin-account!) (zipmap app-names applications))
    ;; (store-routes! generated-routes)
  ))

(def _test-application-path "test/system/person/person.app.edn")

(comment
  (-main _test-application-path))

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

