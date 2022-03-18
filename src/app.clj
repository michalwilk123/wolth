(ns app
  (:require [io.pedestal.http :as http]
            [routing :as rt]))

(def service-map
  {::http/routes rt/routes, ::http/type :jetty, ::http/port 8000})

(defonce server (atom nil))

; for deploy
(defn start [] (http/start (http/create-server service-map)))

; for development
(defn start-dev
  []
  (reset! server
          (http/start (http/create-server (assoc service-map
                                            ::http/join? false)))))

(defn stop-dev [] (http/stop @server))

(defn restart [] (stop-dev) (start-dev))


(def zmienna 12221)
(defn funkcja [x] (= x 21))


; Dev stuff
(comment
  (start-dev)
  (restart)
  (stop-dev))
; function that gets the filepath with configuration and loads whole config


; function that runs the server
