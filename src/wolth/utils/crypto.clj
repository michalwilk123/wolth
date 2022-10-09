(ns wolth.utils.crypto
  (:require [cheshire.core :refer [generate-string parse-string]]
            [cryptohash-clj.api :refer [hash-with verify-with]])
  (:import [java.util Base64]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.security MessageDigest]))

(defn bytes->b64
  [bin-data]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bin-data))

(defn string->b64 [in-text] (bytes->b64 (.getBytes in-text)))

(comment
  (string->b64 "JAKIS TEKST E(#(*JE))"))

(defn map->b64
  [in-map]
  (assert (map? in-map) "Expecting clojure map as input!")
  (let [encoded-map (-> in-map
                        (generate-string))]
    (string->b64 encoded-map)))

(comment
  (map->b64 {:user "name", :value "field"}))

(defn b64->map
  [in-string]
  (assert (string? in-string) "Expecting string as input!")
  (as-> in-string it
    (.decode (Base64/getDecoder) it)
    (String. it)
    (parse-string it true)))

(comment
  (b64->map "eyJ1c2VyIjoibmFtZSIsInZhbHVlIjoiZmllbGQifQ")
  (b64->map "eeJ1c2VyIjoibmFtZSIsInZhbHVlIjoiZmllbGQifQ"))

(defn hmac-256-sign
  "Returns the b64 signature of a string with a given
    key, using a SHA-256 HMAC."
  [payload key]
  (let [mac (Mac/getInstance "HMACSHA256")
        secretKey (SecretKeySpec. (.getBytes key) (.getAlgorithm mac))]
    (->> (doto mac (.init secretKey) (.update (.getBytes payload)))
         .doFinal
         (bytes->b64))))

(comment
  (hmac-256-sign "KLUCZ" "WARTOSC"))

(defn compare-digest
  [value-1 value-2]
  (MessageDigest/isEqual (.getBytes value-1) (.getBytes value-2)))

(comment
  (compare-digest "AAAA" "bbbb")
  (compare-digest "AAAA" "AAAA"))

(defn verify-password
  [cand-password password]
  (assert (some? cand-password))
  (assert (some? password))
  (verify-with :argon2 cand-password password))

(comment
  (verify-password
    "password"
    "100$12$argon2id$v13$uvJ6JRCML8HgSajmLQ3Hvg$waw1JvlHdh2iCzF1qfM/zbTjZYCoIYLUwZoPE4kZjUY$$$")
  (verify-password
    "admin"
    "100$12$argon2id$v13$GpJ/B35dT/W9yGTA5UDkzw$BjfFf3h6yWYdydwHnuk3AtvtjyLmvyQx4sQQsr3eh0s$$$")
  (verify-password
    "password"
    "100$12$argon2id$v13$uvJ6JRCML8HgSajmLQ3Hvg$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA$$$"))


(defn create-password-hash
  [password]
  (assert (string? password))
  (hash-with :argon2 password))

(comment
  (create-password-hash "admin"))