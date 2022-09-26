(ns wolth.utils.auth
  (:require [wolth.db.user :refer [get-user-auth-data]]
            [wolth.utils.crypto :as crypto]
            [clojure.string :as str]
            [wolth.server.exceptions :refer [throw-wolth-exception]])
  (:import [java.io IOException]
           [java.time Instant]))

; Wolth uses custom auhtorization implementation of JWT
; authorization method

(defonce token-header-field "Auth-Token")
(defonce DEFAULT-TOKEN-EXPIRATION-SECONDS (* 60 60 24 7))
(defonce JWT-HEADER "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")



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
        (crypto/compare-digest generated-sig signature))
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


(defn create-user-token
  [body db]
  (if-let [password (body :password)]
    (if-let [username (body :username)]
      (if-let [user-data (get-user-auth-data username db)]
        (if (crypto/verify-password password (user-data :password))
          (build-and-save-jwt-token username
                                    (user-data :role)
                                    (user-data :password)
                                    db)
          (throw-wolth-exception :401 "Wrong password!"))
        (throw-wolth-exception :404 (str "Could not find user: " username)))
      (throw-wolth-exception :400 "Username not provided in request!"))
    (throw-wolth-exception :400 "Password not provided in request!")))

(comment
  (create-user-token {:username "dsadsa"} nil)
  (create-user-token {:username "Adam", :password "password"} nil))

;; (defn token-request [req] {:status 200 :body (str req) })
(defn token-request [req] nil)

(def auth-interceptor
  {:name ::wolth-authorization,
   :enter (fn [context]
            (some-> (context :headers)))})