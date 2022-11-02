(ns wolth.server.routes
  (:require [wolth.server.requests :as requests]
            [wolth.utils.auth :as auth]
            [wolth.utils.misc :refer [generate-full-model-chain]]
            [io.pedestal.log :as log]
            [wolth.server.serializers :as serializers]
            [clojure.string :as str]
            [clojure.set :refer [intersection]]
            [wolth.utils.common :refer [multiple-get]]
            [wolth.server.views :refer [wolth-view-interceptor]]
            [wolth.server.utils :refer
             [utility-interceptor actions-lut create-query-name]]
            [wolth.server.-test-data :refer
             [_routes-related-app-data _serializers_test_app_data]]
            [io.pedestal.http.body-params :as body-params]
            [wolth.server.resolvers :refer [wolth-resolver-interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))


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
        wolth-view-interceptor] :route-name :nazwa-posta-routa]
      ["/person/User/:id/user-admin" :get
       [(body-params/body-params) http/json-body utility-interceptor
        auth/authenticator-interceptor auth/model-authorizer-interceptor
        serializers/model-serializer-interceptor wolth-resolver-interceptor
        wolth-view-interceptor] :route-name :czytanie-zwyklego-usera]
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
                :args [["value"] :int],
                :method :post,
                :arg-source :body}
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
  [app-name serializer-name serializer-operation]
  (let [model-name (get serializer-operation :model)
        base-uri (format "/%s/%s" app-name model-name)
        selector-param (format ":%s-query" model-name)
        select-uri (str base-uri (str "/" selector-param "/") serializer-name)
        create-uri (str base-uri "/" serializer-name)
        route-name (format "%s-%s-%s-" app-name serializer-name model-name)
        interceptors [(body-params/body-params) http/json-body
                      utility-interceptor auth/authenticator-interceptor
                      auth/model-authorizer-interceptor
                      serializers/model-serializer-interceptor
                      wolth-resolver-interceptor wolth-view-interceptor]]
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

(defn generate-full-route-object-for-serializer
  [uri operation]
  (let [interceptor-chain [(body-params/body-params) http/json-body
                           utility-interceptor auth/authenticator-interceptor
                           auth/model-authorizer-interceptor
                           serializers/model-serializer-interceptor
                           wolth-resolver-interceptor wolth-view-interceptor]]
    [uri operation interceptor-chain :route-name
     (keyword (str uri "-" (name operation)))]))

(comment
  (generate-full-route-object-for-serializer "one/two/three" :post))

(defn generate-serializer-url
  [app-name serializer-name chain]
  (format (str app-name "/%s/" serializer-name)
          (str/join "/"
                    (interleave chain
                                (map (comp create-query-name keyword) chain)))))

(comment
  (generate-serializer-url "geoapp" "seriaName" '("Country" "City"))
  (generate-serializer-url "app" "public" '("Person")))

(defn generate-routes-for-serializer
  [app-name serializer-name serializer-structure]
  (for [[obj-chain methods] serializer-structure
        sin-method methods]
    (let [uri (generate-serializer-url app-name serializer-name obj-chain)
          real-method (actions-lut sin-method)]
      (generate-full-route-object-for-serializer uri real-method))))


(comment
  (generate-routes-for-serializer "geography-app"
                                  "regular-view"
                                  (list ['("Country") #{:read :delete}]
                                        ['("City") #{:read}]
                                        ['("Country" "City") #{:read}]
                                        ['("City" "Country") #{:read}])))

(defn- get-model-fields-from-serializer
  [serializer-data]
  (as-> serializer-data it
    (it :operations)
    (map (fn [el] (vals (select-keys el [:read :update :create :delete]))) it)
    (apply concat it)
    (map #(get % :model-fields) it)
    (apply concat it)))
(comment
  (get-model-fields-from-serializer (first (_routes-related-app-data
                                             :serializers))))

(defn- get-relation-structure
  [app-datas]
  (->> app-datas
       (map (fn [it] (map #(assoc % :base (it :name)) (it :relations))))
       (filter not-empty)
       (apply concat)
       (map
         (fn [m]
           (list (multiple-get m [:related-name-inside :base :references])
                 (multiple-get m [:related-name-outside :references :base]))))
       (apply concat)
       (map (fn [el] (vector (first el) (rest el))))
       (into {})))

(comment
  (get-relation-structure (_routes-related-app-data :objects)))

(defn get-method-intersection
  [obj-list flat-struct]
  (apply intersection
    (map (fn [obj-name] (get flat-struct obj-name)) obj-list)))

(comment
  (get-method-intersection (list "Country" "City")
                           {"Country" #{:read :delete}, "City" #{:read}}))

(defn create-intermediate-route-structure
  [app-datas serializer-data]
  (letfn [(create-flat-rt-structure [serializer-oper]
            (vector (serializer-oper :model)
                    (-> serializer-oper
                        (select-keys [:read :update :create :delete])
                        (keys)
                        (set))))]
    (let [relation-pairs (get-relation-structure app-datas)
          relation-fields (get-model-fields-from-serializer serializer-data)
          relevant-pairs (map (fn [el] (relation-pairs el)) relation-fields)
          rt-flat-structure (->> serializer-data
                                 :operations
                                 (map create-flat-rt-structure)
                                 (into {}))]
      (as-> rt-flat-structure it
        (keys it)
        (map (fn [x] (list x)) it)
        (generate-full-model-chain it relevant-pairs 1)
        (map (fn [el]
               (vector el (get-method-intersection el rt-flat-structure)))
          it)))))

(comment
  (create-intermediate-route-structure (_routes-related-app-data :objects)
                                       (first (_routes-related-app-data
                                                :serializers)))
  (create-intermediate-route-structure (_serializers_test_app_data :objects)
                                       (first (_serializers_test_app_data
                                                :serializers))))

(defn depr-generate-routes-for-serializer
  [app-name serializer]
  (apply concat
    (map (partial generate-routes-for-serializer-operation
                  app-name
                  (get serializer :name))
      (get serializer :operations))))

(comment
  (depr-generate-routes-for-serializer
    "person"
    {:allowed-roles ["admin"],
     :name "public",
     :operations [{:update {:fields ["username"]}, :model "User"}
                  {:delete true,
                   :model "Person",
                   :read {:attached [], :fields ["name" "note" "id"]},
                   :update {:fields ["name"]}}]})
  (depr-generate-routes-for-serializer "person"
                                       (get-in _test-app-data
                                               [:serializers 0])))

(defn generate-routes-for-functions
  [app-name function-app-data]
  (assert (vector? function-app-data))
  (let [func-interceptors (vector (body-params/body-params)
                                  http/json-body
                                  utility-interceptor
                                  auth/authenticator-interceptor
                                  auth/function-authorizer-interceptor
                                  serializers/bank-serializer-interceptor
                                  wolth-resolver-interceptor
                                  wolth-view-interceptor)]
    (map
      (fn [fun-data]
        (let [url-name (format "/%s/%s" app-name (fun-data :name))
              route-name (keyword
                           (format "%s-func-%s" app-name (fun-data :name)))
              function-method (fun-data :method)]
          (vector url-name
                  function-method
                  func-interceptors
                  :route-name
                  route-name)))
      function-app-data)))

(comment
  (generate-routes-for-functions "test-app" (_test-app-data :functions)))

(defn- generate-common-utility-routes
  [app-name]
  (let [auth-uri (format "/%s/auth" app-name)
        logout-uri (format "/%s/logout" app-name)]
    (list [auth-uri :post
           [(body-params/body-params) http/json-body utility-interceptor
            auth/token-auth-login-interceptor] :route-name
           (keyword (str app-name "-token-auth-request"))]
          [logout-uri :post
           [(body-params/body-params) http/json-body utility-interceptor
            auth/authenticator-interceptor auth/token-auth-logout-interceptor]
           :route-name (keyword (str app-name "-token-logout-request"))])))

(defn generate-routes-for-app
  [app-name app-data]
  (let [routes (concat
                 (apply concat
                   (map (partial depr-generate-routes-for-serializer app-name)
                     (get app-data :serializers)))
                 (generate-common-utility-routes app-name)
                 (generate-routes-for-functions app-name
                                                (get app-data :functions)))]
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
