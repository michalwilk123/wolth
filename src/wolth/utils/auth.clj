(ns wolth.utils.auth
  (:require
    [wolth.db.user :refer [fetch-user-data save-auth-token]]
    [wolth.utils.crypto :as crypto]
    [wolth.server.utils :as server-utils]
    [wolth.server.config :refer [def-context cursor-pool app-data-container]]
    [wolth.db.utils :refer [get-datasource-from-memory]]
    [next.jdbc :as jdbc]
    [clojure.string :as str]
    [wolth.server.exceptions :refer [throw-wolth-exception def-interceptor-fn]]
    [io.pedestal.http.body-params :as body-params])
  (:import [java.io IOException]
           [java.time Instant]))

; Wolth uses custom auhtorization implementation of JWT
; authorization method

(defonce token-header-field "auth-token")
(defonce DEFAULT-TOKEN-EXPIRATION-SECONDS (* 60 60 24 7))
(defonce JWT-HEADER "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")


(def operations-lut
  {:post :create, :delete :delete, :get :read, :patch :udpate})

(def _test-jwt-token
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFkYW0iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY0MTQ3NzI5LCJ0dGwiOjYwNDgwMH0.kv47aXxC7EbyuR_yg8EpoeRXiHfKon7MJP5LSFcyE0Y")

(def _test-incorrect-jwt-token
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFkYW0iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY0MTQ3NzI5LCJ0dGwiOjYwNDgwMH0.kv47aXxC7EbyuR_yg8EpoeRXiHfKon7MJP5LSFcyE0a")

(def _test_authenticated_ctx
  {:logged-user {:role "admin", :username "adam"},
   :app-name "test-app",
   :request
     {:json-params {:name "Jake"},
      :headers
        {"accept" "*/*",
         "user-agent" "Thunder Client (https://www.thunderclient.com)",
         "connection" "close",
         "host" "localhost:8002",
         "accept-encoding" "gzip, deflate, br",
         "content-length" "20",
         "auth-token"
           "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFkYW0iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY0MTQ3NzI5LCJ0dGwiOjYwNDgwMH0.kv47aXxC7EbyuR_yg8EpoeRXiHfKon7MJP5LSFcyE0Y",
         "content-type" "application/json"},
      :path-info "/test-app/User/public",
      :uri "/test-app/User/public",
      :request-method :post,
      :context-path ""}})

(def _test-request-map
  {:app-name "person",
   :request {:json-params {:username "myAdmin", :password "admin"},
             :headers {"accept" "*/*",
                       "user-agent"
                         "Thunder Client (https://www.thunderclient.com)",
                       "connection" "close",
                       "host" "localhost:8002",
                       "accept-encoding" "gzip, deflate, br",
                       "content-length" "20",
                       "auth-token" _test-jwt-token,
                       "content-type" "application/json"},
             :query-string nil,
             :path-params {},
             :scheme :http,
             :request-method :post,
             :context-path ""}})

(def ^:private _test-app-data
  {:objects
     [{:name "User",
       :fields
         [{:constraints [:not-null :unique], :name "username", :type :str128}
          {:constraints [:not-null], :name "password", :type :password}
          {:constraints [:not-null], :name "role", :type :str128}
          {:constraints [:unique], :name "email", :type :str128}],
       :options [:uuid-identifier]}],
   :functions [{:name "getDate"}],
   :serializers [{:name "public",
                  :allowed-roles ["admin"],
                  :operations
                    [{:model "User",
                      :read {:fields ["author" "content" "id"], :attached []},
                      :update {:fields ["username" "email"]},
                      :create {:fields ["username" "email" "password"],
                               :attached [["role" "regular"]]},
                      :delete true}]}]})

(def-context _test-context
             {cursor-pool {"test-app" (jdbc/get-datasource {:dbtype "h2",
                                                            :dbname "app"})},
              app-data-container {"person" _test-app-data}})

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
  (let [[header payload signature] (parse-jwt-safely jwt-string)]
    (println header payload signature)
    (if (not= header JWT-HEADER)
      (throw-wolth-exception :400 "Unknown wolth exception")
      nil)
    (let [user-data (fetch-user-data (payload :username) app-name)]
      (if (empty? user-data)
        (throw-wolth-exception :404
                               (str "Cannot find user: " (payload :username)))
        (let [oo (println user-data)
              calculated-jwt-string (create-jwt-string payload
                                                       (user-data :password))
              generated-sig (last (str/split calculated-jwt-string #"\."))]
          (if (crypto/compare-digest generated-sig signature)
            (dissoc user-data :password)
            nil))))))

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
  token-request
  [ctx]
  (let [body-params (select-keys (get-in ctx [:request :json-params])
                                 [:username :password])
        app-name (ctx :app-name)]
    (if-let [user-data (fetch-user-data (body-params :username) app-name)]
      (if (or (println (body-params :password) (user-data :password))
              (crypto/verify-password (body-params :password)
                                      (user-data :password)))
        (let [token-parts (vals (select-keys user-data
                                             [:id :username :role :password]))
              jwt-token (apply build-and-save-jwt-token
                          (concat token-parts (list app-name)))]
          (assoc ctx :response {:status 200, :body {:jwt-token jwt-token}}))
        (throw-wolth-exception :403 "Bad password. Access denied"))
      (throw-wolth-exception :404 "Cannot find this user in the database"))))

(comment
  (token-request _test-request-map))

(def token-auth-req-interceptor
  {:name ::TOKEN-WOLTH-INTERCEPTOR, :enter token-request})

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
  (let [translated-method (operations-lut method)
        serializer-objects (serializer :operations)]
    (letfn
      [(find-related-object [table-name]
         (first (filter (fn [x] (= (x :model) table-name)) serializer-objects)))
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

; TODO: TO DZISIAJ MASZ DO ROBOTY!!
#_"sprawdzamy czy istnieje mapa z danymi, jezeli nie to ustawiamy role jako publiczna
   Potem sprawdzamy czy serializer zgadza sie z rola uzytkownika. Jezeli
   nie to rzucamy wyjatek 403"
(def-interceptor-fn
  authorize-user
  [ctx]
  (let [user-data (get ctx :logged-user)
        [app-name serializer-name accessed-tables]
          (server-utils/uri->parsed-info (get-in ctx [:request :uri]) :post)
        app-data (server-utils/get-associated-app-data! app-name)
        serializer (first (filter #(= serializer-name (% :name))
                            (app-data :serializers)))]
    (if (nil? serializer)
      (throw-wolth-exception :400 "Cannot find such view")
      (println user-data))
    (authorize-by-user-role (serializer :allowed-roles) (get user-data :role))
    (authorize-by-user-operation serializer
                                 accessed-tables
                                 (get-in ctx [:request :request-method]))
    (assoc ctx :authorized true)))

(comment
  (_test-context '(authorize-user _test_authenticated_ctx)))

(def authorizer-interceptor
  {:name ::AUTHORIZER-INTERCEPTOR, :enter authorize-user})
