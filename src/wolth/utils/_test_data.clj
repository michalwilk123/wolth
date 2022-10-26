(ns wolth.utils.-test-data)

; =============== TEST DATA FOR loader.clj ================ START

(def _test_application_path "test/system/person/person.app.edn")

(def _loader_test_app_data
  {:functions [{:allowed-roles ["admin"],
                :args [["num" :int]],
                :function-name "nPrimes2",
                :name "primes",
                :path "functions/clojureFunction.clj",
                :type :clojure}
               {:allowed-roles ["admin"],
                :function-name "dateFunc",
                :name "getDate",
                :path "functions/datesCode.clj",
                :args [["num" :int]]}
               {:allowed-roles ["admin"],
                :args [["str" :int]],
                :function-name "isPrime?",
                :name "check-if-prime",
                :path "functions/clojureFunction.clj",
                :type :clojure}],
   :meta {:admin {:name "myAdmin", :password "admin"}, :author "Michal Wilk"},
   :objects [{:fields [{:constraints [:not-null], :name "name", :type :str32}
                       {:name "note", :type :text}],
              :name "Person",
              :options [:uuid-identifier]}
             {:fields [{:name "content", :type :text}],
              :name "Post",
              :options [:uuid-identifier],
              :relations
                [{:name "author", :rel-type :o2m, :references "Person"}]}],
   :persistent-db {:dbname "mydatabase", :dbtype "h2"},
   :serializers [{:allowed-roles ["public"],
                  :name "public",
                  :operations [{:create {:attached [["note" "Testowa notatka"]],
                                         :fields ["name"]},
                                :delete true,
                                :model "Person",
                                :read {:attached [],
                                       :fields ["name" "note" "id"]},
                                :update {:fields ["name"]}}]}]})

; =============== TEST DATA FOR loader.clj ================ END


; =============== TEST DATA FOR auth.clj ================ START
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



(def _test_authenticated_ctx_for_functions
  {:logged-user {:role "admin", :username "adam"},
   :app-name "test-app",
   :request
     {:headers
        {"accept" "*/*",
         "user-agent" "Thunder Client (https://www.thunderclient.com)",
         "connection" "close",
         "host" "localhost:8002",
         "accept-encoding" "gzip, deflate, br",
         "content-length" "20",
         "auth-token"
           "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFkYW0iLCJyb2xlIjoiYWRtaW4iLCJjcmVhdGVkLWF0IjoxNjY0MTQ3NzI5LCJ0dGwiOjYwNDgwMH0.kv47aXxC7EbyuR_yg8EpoeRXiHfKon7MJP5LSFcyE0Y",
         "content-type" "application/json"},
      :path-info "/test-app/getDate",
      :uri "/test-app/getDate",
      :request-method :get,
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

(def _auth_test_app_data
  {:objects
     [{:name "User",
       :fields
         [{:constraints [:not-null :unique], :name "username", :type :str128}
          {:constraints [:not-null], :name "password", :type :password}
          {:constraints [:not-null], :name "role", :type :str128}
          {:constraints [:unique], :name "email", :type :str128}],
       :options [:uuid-identifier]}],
   :functions
     [{:allowed-roles ["admin"], :function-name "datefunc", :name "getDate"}],
   :serializers [{:name "public",
                  :allowed-roles ["admin"],
                  :operations
                    [{:model "User",
                      :read {:fields ["author" "content" "id"], :attached []},
                      :update {:fields ["username" "email"]},
                      :create {:fields ["username" "email" "password"],
                               :attached [["role" "regular"]]},
                      :delete true}]}]})

; =============== TEST DATA FOR auth.clj ================ END