(ns routing
  (:require [io.pedestal.http.route :as route]))


;; (def routes
;;   (route/expand-routes #{["/user" :get (fn [req] nil) :route-name :greet]}))


(def routes-vector nil)

(defn hello-world [req] {:status 200, :body "cxcxz"})
(defn siema [req] {:status 200, :body "dsadsa"})

(def routes
  (route/expand-routes
    '[[["/app"
        {:get hello-world}
        [ "/dupa" {:get siema} ]]]]))

;; (def route-table
;;   (expand-routes '[[["/hello-world" {:get hello-world}]]]))
