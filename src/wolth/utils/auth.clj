(ns wolth.utils.auth
  (:require [wolth.db.user :refer [get-user-auth-data]]
            [wolth.utils.crypto :as crypto]
            [wolth.server.utils :as server-utils]
            [wolth.server.config :refer
             [def-context cursor-pool app-data-container]]
            [wolth.db.utils :refer [get-data-source]]
            [next.jdbc :as jdbc]
            [clojure.string :as str]
            [wolth.server.exceptions :refer [throw-wolth-exception]])
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
  {:json-params {:name "Jake"},
   :protocol "HTTP/1.1",
   :async-supported? true,
   :remote-addr "127.0.0.1",
   :app-name "test-app",
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
   :server-port 8002,
   :content-length 20,
   :logged-user {:role "admin", :username "adam"},
   :content-type "application/json",
   :path-info "/test-app/User/public",
   :character-encoding "UTF-8",
   :uri "/test-app/User/public",
   :server-name "localhost",
   :query-string nil,
   :path-params {},
   :scheme :http,
   :request-method :post,
   :context-path ""})

(def _test-request-map
  {:json-params {:name "Jake"},
   :protocol "HTTP/1.1",
   :async-supported? true,
   :remote-addr "127.0.0.1",
   :headers {"accept" "*/*",
             "user-agent" "Thunder Client (https://www.thunderclient.com)",
             "connection" "close",
             "host" "localhost:8002",
             "accept-encoding" "gzip, deflate, br",
             "content-length" "20",
             "auth-token" _test-jwt-token,
             "content-type" "application/json"},
   :server-port 8002,
   :content-length 20,
   :content-type "application/json",
   :path-info "/app/User/public",
   :character-encoding "UTF-8",
   :uri "/app/User/public",
   :server-name "localhost",
   :app-name "test-app",
   :query-string nil,
   :path-params {},
   :scheme :http,
   :request-method :post,
   :context-path ""})

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
              app-data-container {"test-app" _test-app-data}})

(defn get-current-epoch
  "Get current date since epoch in seconds"
  []
  (-> (Instant/now)
      (inst-ms)
      (quot 1000)))

(comment
  (get-current-epoch))

(defn save-auth-token [db payload] (println "Saving token..." payload))

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
  (create-jwt-string {:username "Michal", :role "admin", :liczba 420}
                     "HASHHASLA"))

(defn fetch-and-validate-jwt-token
  [jwt-string db]
  (let [[header payload signature] (parse-jwt-safely jwt-string)]
    (if (not= header JWT-HEADER)
      (throw-wolth-exception :400 "Unknown wolth exception")
      nil)
    (if-let [user-data (get-user-auth-data (payload :username) db)]
      (let [calculated-jwt-string (create-jwt-string payload
                                                     (user-data :password))
            generated-sig (last (str/split calculated-jwt-string #"\."))]
        (if (crypto/compare-digest generated-sig signature)
          (dissoc user-data :password)
          nil))
      (throw-wolth-exception :404
                             (str "Cannot find such user: "
                                  (payload :username))))))

(comment
  (fetch-and-validate-jwt-token
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFkYW0iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY0MTQ3NzI5LCJ0dGwiOjYwNDgwMH0.kv47aXxC7EbyuR_yg8EpoeRXiHfKon7MJP5LSFcyE0Y"
    nil)
  (fetch-and-validate-jwt-token
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFkYW0iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY0MTQ3NzI5LCJ0dGwiOjYwNDgwMH0.-9uX2yPe-sMmULmE69RF6btB1Rex2aYFtkkH4OjgrRs"
    nil))


(defn build-and-save-jwt-token
  [username role password-hash db]
  (let [token-data {:username username,
                    :role role,
                    :created-at (get-current-epoch),
                    :ttl DEFAULT-TOKEN-EXPIRATION-SECONDS}
        jwt-string (create-jwt-string token-data password-hash)]
    (save-auth-token db jwt-string)
    jwt-string))

(comment
  (build-and-save-jwt-token
    "adam" "admin"
    "100$12$argon2id$v13$qjVHAso9NbIZz1alruEjjg$pobQt1Ij/vk6gmhf1yOCGwNFwoxQzRgQuSFmXBjkg9Y$$$"
      nil))


#_"
  If user has invalid header, bad password, bad username then
  exception is thrown. If header does not exist, user
  is simply unauthorized, but the request is not interrupted"
(defn authenticate-user
  [ctx]
  (if-let [jwt-token (get-in ctx [:headers token-header-field])]
    (let [data-source (get-data-source (ctx :app-name))]
      (if-let [user-data (fetch-and-validate-jwt-token jwt-token data-source)]
        (assoc ctx :logged-user user-data)
        (throw-wolth-exception :401 "Cannot authenticate user!")))
    (assoc ctx :logged-user nil)))

(comment
  (_test-context '(authenticate-user _test-request-map))
  (_test-context '(authenticate-user
                   (update-in
                    _test-request-map
                    [:headers token-header-field]
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
(defn authorize-user
  [ctx]
  (let [user-data (ctx :logged-user)
        [app-name serializer-name accessed-tables]
          (server-utils/uri->parsed-info (ctx :uri) :post)
        app-data (server-utils/get-associated-app-data! app-name)
        serializer (first (filter #(= serializer-name (% :name))
                            (app-data :serializers)))]
    (if (nil? serializer)
      (throw-wolth-exception :400 "Cannot find such view")
      (println user-data))
    (authorize-by-user-role (serializer :allowed-roles) (user-data :role))
    (authorize-by-user-operation serializer
                                 accessed-tables
                                 (ctx :request-method))
    (assoc ctx :authorized true)))

(comment
  (_test-context '(authorize-user _test_authenticated_ctx) ))

(def authorizer-interceptor
  {:name ::AUTHORIZER-INTERCEPTOR, :enter authorize-user})