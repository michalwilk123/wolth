(ns wolth.server.routes
  (:require [honey.sql :as sql]
            [wolth.server.requests :as requests]
            [wolth.utils.auth :as auth]
            [io.pedestal.log :as log]
            [wolth.server.serializers :as serializers]
            [wolth.server.views :refer [view-interceptor]]
            [io.pedestal.interceptor :refer [interceptor]]
            [wolth.server.utils :refer [utility-interceptor]]
            [io.pedestal.http.body-params :as body-params]
            [wolth.server.resolvers :refer [model-resolver-interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [wolth.utils.common :as utils]))



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

(defn hello
  [ctx]
  (log/error :BBBB "NIE ODPALA SIE")
  {:status 200, :body (str "AAAAA" ctx)})

;; IMPORTANT! Expand route must be outside the init function
;; (def route-table
;;   (route/expand-routes [[:hello
;;                          ["/app" {:get 'example}
;;                           ["/:query" {:get
;;                           'requests/build-read-request}]]]]))

;; (defn utility-interceptor-fn [ctx]
;;   (log/info :EEE " ODPALA SIE")
;;   (log/info :OOO ctx)
;;   (assoc ctx :response {:body (str "twoja stara smazy sul: " ctx) :status
;;   200})
;;   )

(def ii
  {:name ::DSAKDNS,
   :enter (fn [x]
            (assoc x
              :response
                {:status 201,
                 :body (str "TESTING MESSAGE  "
                            (select-keys x [:exception-occured :response]))}))})


(def route-table
  (route/expand-routes
    #{["/app/Person/:Person_query" :get 'requests/build-read-request :route-name
       :nazwa-routa]
      ;; ["/app/Person/:Person_query/admin" :get 'requests/build-read-request
      ;;  :route-name :nazwa-routa-num2]
      ["/person/auth" :post
       [(body-params/body-params) http/json-body utility-interceptor
        auth/token-auth-req-interceptor] :route-name :app-token-request]
      ["/app/Person/:Person_query/User/:User_query/admin" :get
       requests/build-read-request :route-name :nazwa-routa-num3]
      ["/person/Person/public" :post
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/authorizer-interceptor
        view-interceptor] :route-name :nazwa-posta-routa]
      ["/person/User/user-regular" :post
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/authorizer-interceptor
        serializers/model-serializer-interceptor model-resolver-interceptor
        view-interceptor] :route-name :tworzenie-zwyklego-usera]
      ["/person/User/:user-query/user-admin/" :get
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/authorizer-interceptor
        serializers/model-serializer-interceptor view-interceptor] :route-name
       :czytanie-zwyklego-usera]
      ["/app2/Person" :post
       [(body-params/body-params) 'http/json-body
        'serializers/model-serializer-interceptor
        'requests/build-create-request] :route-name :nazwa-test-posta-routa]}))


(comment
  route-table)

(comment
  (sql/format (hsql/create-table :foo :if-not-exists)))

(defn generate-routes-for-single-app [app-name])

; TODO: Dokonczyc!!
(defn generate-full-routes
  [app-names app-datas]
  (assert (seq? app-names))
  (assert (seq? app-datas))
  (assert (= (count app-datas) (count app-datas)))
  nil)

(comment
  generate-full-routes
  (list)
  (list))