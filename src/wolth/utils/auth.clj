(ns wolth.utils.auth
  (:require
    [wolth.db.user :refer
     [fetch-user-data save-auth-token remove-user-tokens user-token-exists?]]
    [io.pedestal.log :as log]
    [wolth.utils.common :refer [find-first multiple-get]]
    [wolth.utils.crypto :as crypto]
    [wolth.server.utils :as server-utils]
    [wolth.server.config :refer [def-context cursor-pool app-data-container]]
    [next.jdbc :as jdbc]
    [clojure.string :as str]
    [wolth.utils.-test-data :refer
     [_test-request-map _auth_test_app_data _test_authenticated_ctx
      _test_authenticated_ctx_for_functions]]
    [wolth.server.exceptions :refer [throw-wolth-exception def-interceptor-fn]])
  (:import [java.io IOException]
           [java.time Instant]))

; Wolth uses custom auhtorization implementation of JWT
; authorization method

(defonce token-header-field "auth-token")
(defonce DEFAULT-TOKEN-EXPIRATION-SECONDS (* 60 60 24 7))
(defonce JWT-HEADER "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")


(def-context _test-context
             {cursor-pool {"test-app" (jdbc/get-datasource
                                        {:dbtype "h2", :dbname "mydatabase"})},
              app-data-container {"test-app" _auth_test_app_data}})

(defn get-current-epoch
  "Get current date since epoch in seconds"
  []
  (-> (Instant/now)
      (inst-ms)
      (quot 1000)))

(comment
  (get-current-epoch))



(defn parse-jwt-safely
  [unsafe]
  (let [splitted (str/split unsafe #"\.")
        [header payload signature] splitted]
    (try
      (if (not= (count splitted) 3)
        (throw-wolth-exception
          :400
          "Wrong number of jwt parts. Expecting: header, payload and signature, delimitted by the dot character")
        nil)
      (list header (crypto/b64->map payload) signature)
      (catch IllegalArgumentException b64E
        (throw-wolth-exception
          :400
          (str
            "B64 decode error! User has provided invalid b64 string. More info: "
            (.getMessage b64E))))
      (catch IOException jsonE
        (throw-wolth-exception
          :400
          (str
            "JSON parse Error! User has encoded unparsable b64 string. More info: "
            (.getMessage jsonE)))))))

(comment
  (parse-jwt-safely "eyJ1c2VyIjoibmFtZSIsInZhbHVlIjoiZmllbGQifQ==")
  (parse-jwt-safely "heder.eeJ1c2VyIjoibmFtZSIsInZhbHVlIjoiZmllbGQifQ==.sign")
  (parse-jwt-safely "heder.ZLY FORMAT B64.sign"))

(defn create-jwt-string
  [payload secret]
  (assert (map? payload) "Payload should be an instance of a map")
  (let [encoded-payload (crypto/map->b64 payload)
        signature (crypto/hmac-256-sign (str JWT-HEADER "." encoded-payload)
                                        secret)]
    (str/join "." (list JWT-HEADER encoded-payload signature))))

(comment
  (create-jwt-string
    {:username "myAdmin", :role "admin", :created-at 1664147729, :ttl 604800}
    "HASHHASLA"))

(defn fetch-and-validate-jwt-token
  [jwt-string app-name]
  (if-not (user-token-exists? jwt-string app-name)
    (throw-wolth-exception :401 "Authorization Error. Unknown token")
    (let [[header payload signature] (parse-jwt-safely jwt-string)]
      (if (not= header JWT-HEADER)
        (throw-wolth-exception :400 "Unknown wolth exception")
        nil)
      (let [user-data (fetch-user-data (payload :username) app-name)]
        (if (empty? user-data)
          (throw-wolth-exception :404
                                 (str "Cannot find user: " (payload :username)))
          (let [calculated-jwt-string (create-jwt-string payload
                                                         (user-data :password))
                generated-sig (last (str/split calculated-jwt-string #"\."))]
            (if (crypto/compare-digest generated-sig signature)
              (dissoc user-data :password)
              nil)))))))

(comment
  (fetch-and-validate-jwt-token
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6Im15QWRtaW4iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY1MjE5MTc1LCJ0dGwiOjYwNDgwMH0.1g45U6Srj5xkU6oUxQSdKTqLd_t71gfkMyXGmThsd_E"
    "person")
  (fetch-and-validate-jwt-token
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6Im15QWRtaW4iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY1MjE5MTc1LCJ0dGwiOjYwNDgwMH0.1g45U6Srj5xkU6oUxQSdKTqLd_t71gfkMyXGmThsd_E"
    "person"))


(defn build-and-save-jwt-token
  [user-id username role password-hash app-name]
  (let [token-data {:username username,
                    :role role,
                    :created-at (get-current-epoch),
                    :ttl DEFAULT-TOKEN-EXPIRATION-SECONDS}
        jwt-string (create-jwt-string token-data password-hash)]
    (save-auth-token user-id jwt-string app-name)
    jwt-string))

(comment
  (build-and-save-jwt-token
    "8dbe6592-6103-4d8d-9bfc-c33ddb553ac7"
    "myAdmin" "admin"
    "100$12$argon2id$v13$5mghgFpvKNmkJVtA6EblNw$gorf8qYI4q9nVxY2+v237cE5vOlEQzbFqrUMJfgToOE$$$"
      "person"))

(def-interceptor-fn
  token-login-request
  [ctx]
  (let [body-params (select-keys (get-in ctx [:request :json-params])
                                 [:username :password])
        app-name (ctx :app-name)]
    (and
      (not= 2 (count body-params))
      (throw-wolth-exception
        :400
        "Need username and password to log in. Those body parameters were not provided"))
    (log/info ::token-request
              (str "User tried to log in: " (body-params :username)))
    (if-let [user-data (fetch-user-data (body-params :username) app-name)]
      (if (crypto/verify-password (body-params :password) (user-data :password))
        (let [token-parts (multiple-get user-data
                                        [:id :username :role :password])
              jwt-token (apply build-and-save-jwt-token
                          (concat token-parts (list app-name)))]
          (assoc ctx :response {:status 200, :body {:jwt-token jwt-token}}))
        (throw-wolth-exception :403 "Bad password. Access denied"))
      (throw-wolth-exception :404 "Cannot find this user in the database"))))

(comment
  (token-login-request _test-request-map))

(def token-auth-login-interceptor
  {:name ::TOKEN-LOGIN-WOLTH-INTERCEPTOR, :enter token-login-request})

#_"
  If user has invalid header, bad password, bad username then
  exception is thrown. If header does not exist, user
  is simply unauthorized, but the request is not interrupted"
(def-interceptor-fn
  authenticate-user
  [ctx]
  (if-let [jwt-token (get-in ctx [:request :headers token-header-field])]
    (if-let [user-data (fetch-and-validate-jwt-token jwt-token
                                                     (get ctx :app-name))]
      (assoc ctx :logged-user user-data)
      (throw-wolth-exception :401 "Cannot authenticate user!"))
    (assoc ctx :logged-user nil)))

(comment
  (_test-context '(authenticate-user _test-request-map))
  (_test-context '(authenticate-user
                   (update-in
                    _test-request-map
                    [:request :headers token-header-field]
                    (fn [_] _test-incorrect-jwt-token)))))

(def authenticator-interceptor
  {:name ::AUTHENTICATOR-INTERCEPTOR, :enter authenticate-user})

; TODO: TO DZISIAJ ROBIMY
#_"We expect that user is logged in and authenticated. After that we do actual
   logging out"
(def-interceptor-fn
  token-logout-request
  [ctx]
  (if-let [user-data (ctx :logged-user)]
    (do (log/info ::token-logout-request
                  (format "User: %s is loging out" (ctx :logged-user)))
        (remove-user-tokens (user-data :id) (ctx :app-name))
        (assoc ctx
          :response {:status 200,
                     :body {:message "User logged out successfully"}}))
    (throw-wolth-exception :403 "Cannot log out not logged in user!")))

(comment
  (token-logout-request _test-request-map)
  (token-logout-request (assoc _test-request-map
                          :logged-user {:username "michal", :id "dasdsadsa"})))

(def token-auth-logout-interceptor
  {:name ::TOKEN-LOGOUT-WOLTH-INTERCEPTOR, :enter token-logout-request})


(defn authorize-by-user-role
  [allowed-roles user-role]
  (cond (true? allowed-roles) nil
        (false? allowed-roles)
          (throw-wolth-exception :403 "View temporarily unavailable")
        (not (.contains allowed-roles user-role))
          (throw-wolth-exception
            :403
            "User with this role has no access to this view!"))
  true)

(comment
  (authorize-by-user-role ["admin" "sales"] "admin")
  (authorize-by-user-role true "public")
  (authorize-by-user-role ["admin" "sales"] "public"))

(defn authorize-by-user-operation
  [serializer accessed-tables method]
  (let [translated-method (server-utils/operations-lut method)
        serializer-objects (serializer :operations)]
    (letfn
      [(find-related-object [table-name]
         (find-first (fn [x] (= (x :model) table-name)) serializer-objects))
       (test-object-found [obj]
         (if (nil? obj)
           (throw-wolth-exception :403
                                  (format
                                    "User has no access to this objects: %s"
                                    (str/join ", " accessed-tables)))
           obj))
       (test-method-allowed [obj]
         (if-not (contains? obj translated-method)
           (throw-wolth-exception :405)
           obj))]
      (->> accessed-tables
           (map find-related-object)
           (map test-object-found)
           (run! test-method-allowed)) true)))

(comment
  (authorize-by-user-operation
    {:name "public",
     :allowed-roles ["admin"],
     :operations [{:model "User",
                   :read {:fields ["author" "content" "id"], :attached []},
                   ;;  :update {:fields ["username" "email"]},
                   :create {:fields ["username" "email" "password"],
                            :attached [["role" "regular"]]},
                   :delete true}]}
    ["User"]
    :post))

(def-interceptor-fn
  authorize-user
  [ctx]
  (let [user-data (get ctx :logged-user)
        [app-name serializer-name accessed-tables]
          (server-utils/uri->parsed-info (get-in ctx [:request :uri]) :post)
        app-data (server-utils/get-associated-app-data! app-name)
        serializer (find-first #(= serializer-name (% :name))
                               (app-data :serializers))]
    (and (nil? serializer) (throw-wolth-exception :400 "Cannot find such view"))
    (authorize-by-user-role (serializer :allowed-roles) (get user-data :role))
    (authorize-by-user-operation serializer
                                 accessed-tables
                                 (get-in ctx [:request :request-method]))
    (assoc ctx :authorized true)))

(comment
  (_test-context '(authorize-user _test_authenticated_ctx)))

(def model-authorizer-interceptor
  {:name ::AUTHORIZER-INTERCEPTOR, :enter authorize-user})

(def-interceptor-fn
  function-authorize-user
  [ctx]
  (let [user-data (get ctx :logged-user)
        [app-name function-name]
          (server-utils/uri->parsed-info (get-in ctx [:request :uri]) :bank)
        app-data (server-utils/get-associated-app-data! app-name)
        serializer (find-first #(= function-name (% :name))
                               (app-data :functions))]
    (and (nil? serializer)
         (throw-wolth-exception :400 "Cannot find such function"))
    (authorize-by-user-role (serializer :allowed-roles) (get user-data :role))
    (assoc ctx :authorized true)))

(comment
  (_test-context '(function-authorize-user
                   _test_authenticated_ctx_for_functions)))

(def function-authorizer-interceptor
  {:name ::AUTHORIZER-INTERCEPTOR, :enter function-authorize-user})
