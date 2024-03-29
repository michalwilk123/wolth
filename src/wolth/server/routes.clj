(ns wolth.server.routes
  (:require [wolth.utils.auth :as auth]
            [wolth.utils.misc :refer [generate-full-model-chain]]
            [io.pedestal.log :as log]
            [wolth.server.serializers :as serializers]
            [clojure.string :as str]
            [clojure.set :refer [intersection]]
            [wolth.utils.common :refer [multiple-get tee]]
            [wolth.server.views :refer [wolth-view-interceptor]]
            [wolth.server.path :refer [create-query-name]]
            [wolth.server.utils :refer [utility-interceptor actions-lut]]
            [wolth.server.-test-data :refer
             [_test-app-data-w-relations _serializers_test_app_data
              _test-app-data-w-relations-v2]]
            [io.pedestal.http.body-params :as body-params]
            [wolth.server.resolvers :refer [wolth-resolver-interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [wolth.db.user :as user]))

(def methods-allowed-for-uriql #{:get :patch :delete})

(def ^:private _test-app-data
  {:functions [{:function-name "timeSince",
                :name "daysSince",
                :allowed-roles true,
                :arguments [["value"] :int],
                :path "functions/clojureFunction.clj",
                :method :post,
                :arg-source :body}
               {:function-name "nPrimes2",
                :name "primes",
                :allowed-roles ["admin"],
                :method :get,
                :arg-source :query,
                :path "functions/clojureFunction.clj"}],
   :serializers [{:name "public",
                  :operations [{:create {:attached [["note" "Testowa notatka"]],
                                         :fields ["name"]},
                                :delete true,
                                :model "Person",
                                :read {:attached [],
                                       :fields ["name" "note" "id"]},
                                :update {:fields ["name"]}}]}]})


(defn- get-model-fields-from-serializer
  [serializer-data]
  (as-> serializer-data it
    (it :operations)
    (map (fn [el] (vals (select-keys el [:read :update :create :delete]))) it)
    (apply concat it)
    (map #(get % :model-fields) it)
    (apply concat it)))

(comment
  (get-model-fields-from-serializer (first (_test-app-data-w-relations
                                             :serializers))))

;; TODO: TUTAJ DODAJ TABELE Z UZYTKOWNIKAMI
(defn- get-relation-structure
  [app-datas]
  (->> app-datas
       (map (fn [it] (map #(assoc % :base (it :name)) (it :relations))))
       (filter not-empty)
       (apply concat)
       (map
         (fn [m]
           (list (multiple-get m [:relation-name-here :base :references])
                 (multiple-get m [:relation-name-outside :references :base]))))
       (apply concat)
       (map (fn [el] (vector (first el) (rest el))))
       (into {})))

(comment
  (get-relation-structure (_test-app-data-w-relations :objects))
  (get-relation-structure (_test-app-data-w-relations-v2 :objects)))

(defn get-method-intersection
  [obj-list flat-struct]
  (if (= (count obj-list) 1)
    (->> obj-list
         (first)
         (get flat-struct))
    (apply (partial intersection #{:read})
      (map (fn [obj-name] (get flat-struct obj-name)) obj-list))))

(comment
  (get-method-intersection (list "Country" "City")
                           {"Country" #{:read :delete}, "City" #{:read}})
  (get-method-intersection (list "Country" "City")
                           {"Country" #{:read :delete :create},
                            "City" #{:read :update :create :delete}}))


(defn create-intermediate-route-structure
  [objects serializer-data]
  (letfn [(create-flat-rt-structure [serializer-oper]
            (vector (serializer-oper :model)
                    (-> serializer-oper
                        (select-keys [:read :update :create :delete]) ; only
                                                                      ; read
                                                                      ; supported
                        (keys)
                        (set))))]
    (let [relation-pairs (get-relation-structure objects)
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
  (create-intermediate-route-structure (_test-app-data-w-relations :objects)
                                       (first (_test-app-data-w-relations
                                                :serializers)))
  (create-intermediate-route-structure (_test-app-data-w-relations-v2 :objects)
                                       (first (_test-app-data-w-relations-v2
                                                :serializers)))
  (create-intermediate-route-structure (_serializers_test_app_data :objects)
                                       (first (_serializers_test_app_data
                                                :serializers))))

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
  [app-name serializer-name chain &
   {:keys [add-query-param], :or {add-query-param false}}]
  (format
    (str "/" app-name "/%s/" serializer-name)
    (str/join "/"
              (if add-query-param
                (interleave chain (map (comp create-query-name keyword) chain))
                chain))))

(comment
  (generate-serializer-url "geoapp"
                           "seriaName" '("Country" "City")
                           :add-query-param true)
  (generate-serializer-url "geoapp" "seriaName" '("Country" "City"))
  (generate-serializer-url "app" "public" '("Person")))


(defn generate-routes-from-serializer-struct
  [app-name serializer-name serializer-structure]
  (for [[obj-chain methods] serializer-structure
        sin-method methods]
    (let [translated-method (actions-lut sin-method)
          uri (generate-serializer-url app-name
                                       serializer-name
                                       obj-chain
                                       :add-query-param
                                       (methods-allowed-for-uriql
                                         translated-method))]
      (generate-full-route-object-for-serializer uri translated-method))))


(comment
  (generate-routes-from-serializer-struct "geoapp"
                                          "regular-view"
                                          (list ['("Country") #{:read :delete}]
                                                ['("City") #{:read}]
                                                ['("Country" "City") #{:read}]
                                                ['("City" "Country")
                                                 #{:read}])))

(defn generate-routes-for-serializers
  [app-name objects serializers]
  (for [serializer serializers]
    (let [serializer-name (serializer :name)]
      (->> serializer
           (create-intermediate-route-structure objects)
           (generate-routes-from-serializer-struct app-name serializer-name)))))

(comment
  (generate-routes-for-serializers "person"
                                   (_serializers_test_app_data :objects)
                                   (_serializers_test_app_data :serializers))
  (generate-routes-for-serializers "kartaPracy"
                                   (_test-app-data-w-relations-v2 :objects)
                                   (_test-app-data-w-relations-v2 :serializers))
  (generate-routes-for-serializers "geoapp"
                                   (_test-app-data-w-relations :objects)
                                   (_test-app-data-w-relations :serializers)))


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
        logout-uri (format "/%s/logout" app-name)
        my-user-info (format "/%s/whoami" app-name)]
    (list [auth-uri :post
           [(body-params/body-params) http/json-body utility-interceptor
            auth/token-auth-login-interceptor] :route-name
           (keyword (str app-name "-token-auth-request"))]
          [logout-uri :post
           [(body-params/body-params) http/json-body utility-interceptor
            auth/authenticator-interceptor auth/token-auth-logout-interceptor]
           :route-name (keyword (str app-name "-token-logout-request"))]
          [my-user-info :get
           [(body-params/body-params) http/json-body utility-interceptor
            auth/authenticator-interceptor user/my-user-info-interceptor]
           :route-name (keyword (str app-name "user-info-request"))])))

(defn generate-routes-for-app
  [app-name app-data]
  (letfn
    [(log-routes [routes]
       (log/info ::generate-routes-for-app "Creating routes")
       (for [route routes]
         (let [f-string
                 (format "Method: %s, URL: %s;" (second route) (first route))]
           (log/info ::generate-routes-for-app f-string)
           (println f-string))))]
    (as-> (generate-routes-for-serializers app-name
                                           (app-data :objects)
                                           (app-data :serializers))
      it
      (apply concat it)
      (concat it (generate-common-utility-routes app-name))
      (concat it
              (generate-routes-for-functions app-name
                                             (get app-data :functions [])))
      (tee log-routes it))))

(comment
  (generate-routes-for-app "test-app" _test-app-data)
  (generate-routes-for-app "geoapp" _test-app-data-w-relations))

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
  (generate-routes (list "test-app") (list _test-app-data))
  (generate-routes (list "test-app" "geoapp")
                   (list _test-app-data _test-app-data-w-relations)))

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
