(ns wolth.start
  (:gen-class)
  (:require [io.pedestal.http :as http]
            [wolth.server.config :refer [wolth-routes]]
            [wolth.server.routes :as r]
            [wolth.server.utils :refer [app-path->app-name]]
            [wolth.utils.loader :refer
             [create-admin-account! create-sql-tables! load-application!
              load-bank-functions! load-user-functionalities store-applications!
              store-db-connections! test-application-file!]]
            [wolth.utils.spec :refer [explain-wolth-spec wolth-config-valid?]]))

(def initial
  {:env :dev,
   ;; do not block thread that starts web server
   ::http/join? false,
   ::http/type :jetty,
   ::http/port 8002,
   ::http/router :linear-search,
   ;;  ::http/routes (fn [] (deref #'r/route-table)),
   ::http/routes (fn [] (deref wolth-routes)),
   ::http/resource-path "/public",
   ::http/container-options {:h2c? true, :h2? false, :ssl? false},
   ::http/allowed-origins {:creds true, :allowed-origins (constantly true)},
   ::http/secure-headers {:content-security-policy-settings {:object-src
                                                               "'none'"}}})

(defonce server-instance (atom initial))

(defn create-server [] (doall (swap! server-instance http/create-server)))

(defn start-server [] (http/start @server-instance))

(defn stop-server [] (http/stop @server-instance))

(def _person-application-path "test/system/person/person.app.edn")
(def _todo-application-path "test/system/todo/todo.app.edn")
(def _kartaPracy-application-path "test/system/karta_pracy/kartaPracy.app.edn")

(defn configure-wolth
  [& app-paths]
  (run! test-application-file! app-paths)
  (let [applications (->> app-paths
                          (map load-application!)
                          (map load-user-functionalities))
        app-names (map app-path->app-name app-paths)
        generated-routes (r/generate-routes app-names applications)]
    (store-applications! app-names applications)
    (store-db-connections! app-names applications)
    (load-bank-functions! app-names applications)
    (create-sql-tables! app-names applications)
    (run! (partial apply create-admin-account!) (zipmap app-names applications))
    (reset! wolth-routes generated-routes)
    nil))

(comment
  (configure-wolth _person-application-path)
  (configure-wolth _todo-application-path)
  (configure-wolth _kartaPracy-application-path))


(defn init-server
  [app-paths]
  (apply configure-wolth app-paths)
  (create-server)
  (start-server))

(comment
  (init-server (list _todo-application-path _person-application-path)))

(defn configs-validated?
  [app-paths]
  (->> app-paths
       (map load-application!)
       (every? wolth-config-valid?)))

(comment
  (configs-validated? (list _todo-application-path)))

(defn run-checks-on-wolth-configs
  [app-paths]
  (->> app-paths
       (map load-application!)
       (run! explain-wolth-spec)))

(comment
  (run-checks-on-wolth-configs (list _todo-application-path)))

(defn -main
  ([command & app-paths]
   (case command
     "run"
       (if (configs-validated? app-paths)
         (init-server app-paths)
         (println
           "Errors occured. Cannot start the server. You can check the errors by running 'check' command or try to run incorrect schema by running 'force-run' command"))
     "force-run" (init-server app-paths)
     "check" (run-checks-on-wolth-configs app-paths)
     (-main)))
  ([]
   (println
     "Wolth by Michał Wilk
This is a tool to generate web applications based 
on text configuration.
Available commands:
  * run - run all applications by supplied paths (run checks)
  * force-run - run all applications by supplied paths (dont run checks)
  * check - check for errors in given app paths")))

(comment
  (-main "lalala")
  (-main)
  (-main "check" _todo-application-path))

(comment
  (create-server)
  ;; (reset! server-instance)
  (@server-instance)
  (reset! server-instance initial)
  (start-server)
  (stop-server))

