(ns wolth.server.routes
  (:require [honey.sql :as sql]
            [wolth.server.requests :as requests]
            [wolth.utils.auth :as auth]
            [io.pedestal.log :as log]
            [wolth.server.serializers :as serializers]
            [wolth.server.views :refer [model-view-interceptor]]
            [io.pedestal.interceptor :refer [interceptor]]
            [wolth.server.utils :refer [utility-interceptor]]
            [io.pedestal.http.body-params :as body-params]
            [wolth.server.resolvers :refer [model-resolver-interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [wolth.utils.common :as utils]))



(def routes '[[:hello ["/hello-world" {:get hello-world}]]])

(defn hello-world [req] {:status 200, :body "EJSDNJDSANK Worlddjdjd"})

(defn example [req] {:status 200, :body "JESTEM ENDPOINTEM"})

(defn hello
  [ctx]
  (log/error :BBBB "NIE ODPALA SIE")
  {:status 200, :body (str "AAAAA" ctx)})

(def ii
  {:name ::DSAKDNS,
   :enter (fn [x]
            (assoc x
              :response {:status 201,
                         :body (str "TESTING MESSAGEE  "
                                    (select-keys x
                                                 [:exception-occured :response
                                                  :request]))}))})

(defn test-resp [ctx] {:status 200, :body "OK"})


(def route-table
  (route/expand-routes
    #{["/app/Person/:Person_query" :get 'requests/build-read-request :route-name
       :nazwa-routa]
      ["/app/Person/:Person_query/admin" :get 'requests/build-read-request
       :route-name :nazwa-routa-num2]
      ["/person/auth" :post
       [(body-params/body-params) http/json-body utility-interceptor
        auth/token-auth-login-interceptor] :route-name :app-token-request]
      ["/app/Person/:Person_query/User/:User_query/admin" :get
       requests/build-read-request :route-name :nazwa-routa-num3]
      ["/person/Person/public" :post
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/model-authorizer-interceptor
        model-view-interceptor] :route-name :nazwa-posta-routa]
      ["/person/User/:id/user-admin" :get
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/model-authorizer-interceptor
        serializers/model-serializer-interceptor model-resolver-interceptor
        model-view-interceptor] :route-name :czytanie-zwyklego-usera]
      ["/person/User/user-regular" :post
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/model-authorizer-interceptor
        ; serializers/model-serializer-interceptor
        ii] :route-name :tworzenie-zwyklego-usera]
      ["/app2/Person" :post
       [(body-params/body-params) 'http/json-body
        'serializers/model-serializer-interceptor
        'requests/build-create-request] :route-name :nazwa-test-posta-routa]}))


(def url-for (route/url-for-routes route-table))

(comment
  (first (clojure.pprint/pprint route-table))
  (url-for :czytanie-zwyklego-usera {:hehe 2222})
  (url-for :tworzenie-zwyklego-usera))


(def ^:private _test-app-data
  {:functions [{:funcName "timeSince",
                :name "daysSince",
                :allowed-roles true,
                :type :java}
               {:funcName "nPrimes2",
                :name "primes",
                :path "clojureFunction",
                :allowed-roles ["admin"],
                :type :clojure}],
   :serializers [{:name "public",
                  :operations [{:create {:attached [["note" "Testowa notatka"]],
                                         :fields ["name"]},
                                :delete true,
                                :model "Person",
                                :read {:attached [],
                                       :fields ["name" "note" "id"]},
                                :update {:fields ["name"]}}]}]})


(defn generate-routes-for-serializer-operation
  [app-name serializer-name serializer-operation & [related-models]]
  (let [model-name (get serializer-operation :model)
        base-uri (format "/%s/%s" app-name model-name)
        ;; detail-uri (str base-uri "/:id/" serializer-name)
        selector-param (format ":%s-query" model-name)
        select-uri (str base-uri (str "/" selector-param "/") serializer-name)
        create-uri (str base-uri "/" serializer-name)
        route-name (format "%s-%s-%s-" app-name serializer-name model-name)
        interceptors [(body-params/body-params) http/json-body
                      utility-interceptor auth/authenticator-interceptor
                      auth/model-authorizer-interceptor
                      serializers/model-serializer-interceptor
                      model-resolver-interceptor model-view-interceptor]]
    (cond-> (list)
      (get serializer-operation :read)
        (conj [select-uri :get interceptors :route-name
               (keyword (str route-name "-get"))])
      (get serializer-operation :create)
        (conj [create-uri :post interceptors :route-name
               (keyword (str route-name "-post"))])
      (get serializer-operation :update)
        (conj [select-uri :patch interceptors :route-name
               (keyword (str route-name "-patch"))])
      (get serializer-operation :delete)
        (conj [select-uri :delete interceptors :route-name
               (keyword (str route-name "-delete"))]))))

(comment
  (generate-routes-for-serializer-operation
    "person"
    "public"
    {:create {:attached [["note" "Testowa notatka"]], :fields ["name"]},
     :delete true,
     :model "Person",
     :read {:attached [], :fields ["name" "note" "id"]},
     :update {:fields ["name"]}})
  (generate-routes-for-serializer-operation
    "person"
    "public"
    {:delete true,
     :model "Person",
     :read {:attached [], :fields ["name" "note" "id"]},
     :update {:fields ["name"]}}))

(defn generate-routes-for-serializer
  [app-name serializer]
  (apply concat
    (map (partial generate-routes-for-serializer-operation
                  app-name
                  (get serializer :name))
      (get serializer :operations))))

(comment
  (generate-routes-for-serializer
    "person"
    {:allowed-roles ["admin"],
     :name "public",
     :operations [{:update {:fields ["username"]}, :model "User"}
                  {:delete true,
                   :model "Person",
                   :read {:attached [], :fields ["name" "note" "id"]},
                   :update {:fields ["name"]}}]})
  (generate-routes-for-serializer "person"
                                  (get-in _test-app-data [:serializers 0])))

#_"
   1. Sprawdzam gdzie jest funkcja w konfiguracji
   2. Ładuje funkcje w namespace-ie pod odpowiednim miejsce
   "
(defn generate-routes-for-functions [app-name functions] (list))

(defn generate-routes-for-app
  [app-name app-data]
  (let [auth-uri (format "/%s/auth" app-name)
        logout-uri (format "/%s/logout" app-name)
        routes
          (concat
            (apply concat
              (map (partial generate-routes-for-serializer app-name)
                (get app-data :serializers)))
            (list [auth-uri :post
                   [(body-params/body-params) http/json-body utility-interceptor
                    auth/token-auth-login-interceptor] :route-name
                   (keyword (str app-name "-token-auth-request"))])
            (list [logout-uri :post
                   [(body-params/body-params) http/json-body utility-interceptor
                    auth/token-auth-login-interceptor] :route-name
                   (keyword (str app-name "-token-logout-request"))])
            (apply concat
              (map (partial generate-routes-for-functions app-name)
                (get app-data :functions))))]
    (run!
      (fn [rt]
        (let [f-string (format "Method: %s, URL: %s;" (second rt) (first rt))]
          (log/info ::generate-routes-for-app f-string)
          (println f-string)))
      routes)
    routes))

(comment
  (generate-routes-for-app "test-app" _test-app-data))

(defn generate-routes
  [app-names app-datas]
  (assert (seq? app-names))
  (assert (seq? app-datas))
  (->> (zipmap app-names app-datas)
       (map (partial apply generate-routes-for-app))
       (apply concat)
       (set)
       (route/expand-routes)))

(comment
  (generate-routes (list "test-app") (list _test-app-data)))
