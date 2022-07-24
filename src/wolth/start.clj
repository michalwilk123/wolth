(ns wolth.start
  (:gen-class)
  (:require [wolth.utils.cli :as cli]
            [io.pedestal.http.route :as route]
            [wolth.utils.loader :refer [load-everything]]
            [io.pedestal.http :as http]))

(defn exit [status msg] (println msg) (println status))

(defn create-server-config
  [routes]
  {:env :dev,
   ;; do not block thread that starts web server
   ::http/join? false,
   ::http/type :jetty,
   ::http/port 8001,
   ::http/routes #(route/expand-routes routes),
   ;; ::http/routes #(route/expand-routes (deref #'service/routes-v2)),
   ::http/resource-path "/public",
   ::http/container-options {:h2c? true, :h2? false, :ssl? false},
   ::http/allowed-origins {:creds true, :allowed-origins (constantly true)},
   ::http/secure-headers {:content-security-policy-settings {:object-src
                                                               "'none'"}}})

(defn run-server
  "The entry-point for 'lein run-dev'"
  [server-config]
  (println "\nCreating your [DEV] server...")
  (-> server-config
      http/default-interceptors
      http/dev-interceptors
      http/create-server
      http/start))


(defn run-application
  [args]
  (let [{:keys [action options exit-message ok?]} (cli/validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [generated-server (load-everything (options :applications)
                                              (options :modules))]
        (case action
          "help" (println "pomocna wiadomość")
          "run" (-> generated-server
                    (create-server-config)
                    (run-server))
          "dry-run" (println generated-server)
          (throw (RuntimeException. (format "Unknown command: %s" action))))))))

      ;; 

(def test-path "test/system/hello_world/apps/hello-world.app.edn")

(defn -main
  "The entry point of lein run"
  [& args]
  (if (seq? args) (run-application args) (cli/display-commands)))

(comment
  ;; (case action
  ;;         "run" (println "DZIALAM" options)
  ;;         "dry-run" (println options)
  ;;         "help" (println "pomocna wiadomość"))
  (-main "dry-run" "-A" test-path))