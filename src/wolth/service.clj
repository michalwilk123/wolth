(ns wolth.service
  (:require [clojure.data.json :as json]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [ring.util.response :as ring-resp]
            [wolth.utils.file-utils :as file-utils]))


(def ex-interceptor
  {:name ::orzel,
   :leave (fn [context]
            (assoc context
              :headers {"Content-Type" "application/json"}
              :response (json/write-str (get-in [:response :body] {}))))})

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - kjkdsnakdksa %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page [request] (ring-resp/response "oooooooooooooooo"))

;; (defn home-page [r] (ring-resp/response "ajdnsakjnd"))
;; (def home-page (fn [r] (ring-resp/response "aj")))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes
  #{["/" :get (conj common-interceptors `about-page)]
    ["/about" :get
     (conj common-interceptors (fn [r] (ring-resp/response "oooooooooooooo")))
     :route-name :flamenko]})

(def test-path "test/system/hello_world/apps/hello-world.app.edn")

(def routes-v2 (file-utils/routes-object-for-single-application test-path))



;; Map-based routes
;; (def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;;                   :get home-page}
;;               "/about" {:get about-page}})

;; Terse/Vector-based routes
;; (def routes
;;  `[[["/" {:get home-page}
;;      ^:interceptors [(body-params/body-params) http/html-body]
;;      ["/about" {:get about-page}]]]])


;; Consumed by wolth.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service
  {:env :prod,
   ;; You can bring your own non-default interceptors. Make
   ;; sure you include routing and set it up right for
   ;; dev-mode. If you do, many other keys for configuring
   ;; default interceptors will be ignored.
   ;; ::http/interceptors []
   ::http/routes routes,
   ;; Uncomment next line to enable CORS support, add
   ;; string(s) specifying scheme, host and port for
   ;; allowed source(s):
   ;;
   ;; "http://localhost:8080"
   ;;
   ;;::http/allowed-origins ["scheme://host:port"]
   ;; Tune the Secure Headers
   ;; and specifically the Content Security Policy appropriate to your
   ;; service/application
   ;; For more information, see: https://content-security-policy.com/
   ;;   See also: https://github.com/pedestal/pedestal/issues/499
   ;;::http/secure-headers {:content-security-policy-settings {:object-src
   ;;"'none'"
   ;;                                                          :script-src
   ;;                                                          "'unsafe-inline'
   ;;                                                          'unsafe-eval'
   ;;                                                          'strict-dynamic'
   ;;                                                          https: http:"
   ;;                                                          :frame-ancestors
   ;;                                                          "'none'"}}
   ;; Root for resource interceptor that is available by default.
   ::http/resource-path "/public",
   ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
   ;;  This can also be your own chain provider/server-fn --
   ;;  http://pedestal.io/reference/architecture-overview#_chain_provider
   ::http/type :jetty,
   ;;::http/host "localhost"
   ::http/port 8001,
   ;; Options to pass to the container (Jetty)
   ::http/container-options {:h2c? true,
                             :h2? false,
                             ;:keystore "test/hp/keystore.jks"
                             ;:key-password "password"
                             ;:ssl-port 8443
                             :ssl? false
                             ;; Alternatively, You can specify you're own Jetty
                             ;; HTTPConfiguration
                             ;; via the
                             ;; `:io.pedestal.http.jetty/http-configuration`
                             ;; container option.
                             ;:io.pedestal.http.jetty/http-configuration
                             ;(org.eclipse.jetty.server.HttpConfiguration.)
                            }})
