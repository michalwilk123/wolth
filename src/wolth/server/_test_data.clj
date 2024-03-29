(ns wolth.server.-test-data
  (:require [wolth.utils.loader :refer [create-namespace-name]]))


; =============== TEST DATA FOR utils.clj ================ START

(def _server_test_app_data
  {:objects
     [{:fields [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
                {:constraints [:not-null], :name "name", :type :str32}
                {:name "note", :type :text}],
       :name "Person"}
      {:name "User",
       :fields
         [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
          {:constraints [:not-null :unique], :name "username", :type :str128}
          {:constraints [:not-null], :name "password", :type :password}
          {:constraints [:not-null], :name "role", :type :str128}
          {:constraints [:unique], :name "email", :type :str128}]}],
   :functions [{:name "getDate"}],
   :serializers [{:name "public",
                  :allowed-roles ["admin"],
                  :operations
                    [{:model "User",
                      :read {:fields ["author" "content" "id"], :attached []},
                      :update {:fields ["username" "email"]},
                      :create {:fields ["username" "email" "password"],
                               :attached [["role" "regular"]]},
                      :delete true}
                     {:model "Person", :create {:fields ["note" "name"]}}]}]})
; =============== TEST DATA FOR utils.clj ================ END

; =============== TEST DATA FOR serializers.clj ================ START
(def _test-admin-serializer-spec
  [{:model "User", :read true, :update true, :create true, :delete true}])

(def _test-serializer-spec
  {:name "public",
   :allowed-roles true,
   :operations [{:model "User",
                 :read {:fields ["email" "username" "id"],
                        :additional-query "filter(\"id\"==<:user-id>)"},
                 :update {:fields ["username" "email"],
                          :attached [["lastModified" :today-date]]},
                 :create {:fields ["username" "email" "password"],
                          :attached [["role" "regular"]
                                     ["lastModified" :today-date]]},
                 :delete true}]})

(def _test-object-spec
  {:name "User",
   :fields [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
            {:constraints [:not-null :unique], :name "username", :type :str128}
            {:constraints [:not-null], :name "password", :type :password}
            {:constraints [:not-null], :name "role", :type :str128}
            {:name "lastModified", :type :date-tz}
            {:constraints [:unique], :name "email", :type :str128}]})

(def _test-object-spec-with-relations-1
  {:name "Country",
   :fields [{:name "id", :type :id, :constraints [:id-constraints]}
            {:constraints [:not-null], :name "countryName", :type :str128}
            {:constraints [:unique], :name "code", :type :int}
            {:constraints [:unique], :name "president", :type :str128}]})

(def _test-object-spec-with-relations-2
  {:name "City",
   :fields [{:name "id", :type :id, :constraints [:id-constraints]}
            {:constraints [:not-null], :name "cityName", :type :str128}
            {:constraints [], :name "creationData", :type :str128}
            {:constraints [], :name "major", :type :str128}],
   :relations [{:name "country_id",
                :references "Country",
                :relation-type :o2m,
                :relation-name-here "country",
                :relation-name-outside "cities"}]})

(def _test-normalized-fields
  {:id "65ebc5a7-348c-4bb7-a58b-54d96a1b41bf",
   :username "Mariusz",
   :password
     "100$12$argon2id$v13$qen5BpBkZOs7qT9abjb9iA$eq9Fr5Im3Nqx35GHDIMg7ADbyq09zvuoV+sbvNlYYWI$$$",
   :email "mariusz@gmail.com",
   :role "regular"})

(def _test-json-body
  {:username "Mariusz", :password "haslo", :email "mariusz@gmail.com"})

(def _test-post-request-map
  {:request {:json-params _test-json-body,
             :remote-addr "127.0.0.1",
             :uri "/test-app/User/public",
             :path-params {},
             :request-method :post}})

(def _test-get-request-map
  {:logged-user {:username "Przemek", :id 22211},
   :request {:json-params {},
             :uri "/test-app/User/*/public",
             :path-params {:User-query "*"},
             :request-method :get}})

(def _test-patch-request-map
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {:username "Marek"},
             :uri "/test-app/User/\"username\"=='Przemek'/public",
             :path-params {:User-query "\"username\"=='Przemek'"},
             :request-method :patch}})

(def _test-delete-request-map
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {},
             :uri "/test-app/User/*/public",
             :path-params {:User-query "\"username\"=='Przemek'"},
             :request-method :delete}})

(def _test-bank-request-map
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {:test-val "123"},
             :uri "/test-app/getDate",
             :query-params {:num "50"},
             :request-method :delete}})

(def _serializers_test_app_data
  {:objects
     [{:name "User",
       :fields
         [{:name "id", :type :uuid, :constraints [:uuid-constraints]}
          {:constraints [:not-null :unique], :name "username", :type :str128}
          {:constraints [:not-null], :name "password", :type :password}
          {:constraints [:not-null], :name "role", :type :str128}
          {:constraints [:unique], :name "email", :type :str128}]}],
   :functions [{:allowed-roles ["admin"],
                :function-name "datefunc",
                :name "getDate",
                :method :get,
                :arg-source :query,
                :arguments [["num" :int]]}],
   :serializers
     [{:name "public",
       :allowed-roles ["admin"],
       :operations [{:model "User",
                     :read {:fields ["username" "email"],
                            :additional-query "filter(\"author\"==<:user-id>)"},
                     :update {:fields ["username" "email"]},
                     :create {:fields ["username" "email" "password"],
                              :attached [["role" "regular"]]},
                     :delete true}]}]})

; =============== TEST DATA FOR serializers.clj ================ END


; =============== TEST DATA FOR resolvers.clj ================ START

(def _resolver_model_ctx_test_data
  {:json-params
     {:username "Mariusz", :password "haslo", :email "mariusz@gmail.com"},
   :protocol "HTTP/1.1",
   :async-supported? true,
   :remote-addr "127.0.0.1",
   :headers {"accept" "*/*",
             "user-agent" "Thunder Client (https://www.thunderclient.com)",
             "connection" "close",
             "host" "localhost:8002",
             "accept-encoding" "gzip, deflate, br",
             "content-length" "20",
             "content-type" "application/json"},
   :server-port 8002,
   :content-length 20,
   :content-type "application/json",
   :path-info "/app/User/public",
   :character-encoding "UTF-8",
   :uri "/app/User/public",
   :server-name "localhost",
   :sql-query
     ["INSERT INTO User (id, username, password, email, role) VALUES (?, ?, ?, ?, ?)"
      "ab6b029c-f98b-4b5d-b7a0-faa9a1d83db3" "Mariusz"
      "100$12$argon2id$v13$hgN/r4flg4x904r398B9kg$QSAXLGtxKeZ8s9eyYdK5FTNyREW9SirLclEFAa8WhIc$$$"
      "mariusz@gmail.com" "regular"],
   :exception-occured false,
   :app-name "app",
   :query-string nil,
   :path-params {},
   :scheme :http,
   :request-method :post,
   :context-path ""})

(def _resolver_test_function_data
  [{:allowed-roles ["admin"],
    :function-name "dateFunc",
    :name "getDate",
    :path "functions/datesCode.clj",
    :arguments [["num" :int]]}])

(def _resolver_func_test_namespace
  (create-ns (create-namespace-name "test-app")))

(def _resolver_func_ctx_test_data
  {:logged-user {:username "Przemek", :id 22222},
   :app-name "test-app",
   :request {:json-params {},
             :uri "/test-app/getDate",
             :query-params {:num "50"},
             :request-method :delete},
   :function-data {:function-name "getDate", :function-args '(50)}})

; =============== TEST DATA FOR resolvers.clj ================ END


; =============== TEST DATA FOR routes.clj ================ START

(def _test-app-data-w-relations
  {:objects
     [{:name "Country",
       :fields [{:name "id", :type :id, :constraints [:id-constraints]}
                {:constraints [:not-null], :name "countryName", :type :str128}
                {:constraints [:unique], :name "code", :type :int}
                {:constraints [:unique], :name "president", :type :str128}]}
      {:name "City",
       :fields [{:name "id", :type :id, :constraints [:id-constraints]}
                {:constraints [:not-null], :name "cityName", :type :str128}
                {:constraints [], :name "creationData", :type :str128}
                {:constraints [], :name "major", :type :str128}],
       :relations [{:name "country_id",
                    :references "Country",
                    :relation-type :o2m,
                    :relation-name-here "country",
                    :relation-name-outside "cities"}]}],
   :serializers
     [{:name "regular-view",
       :allowed-roles ["regular"],
       :operations
         [{:model "Country",
           :delete true,
           :read {:fields ["countryName" "code" "president" "cities"],
                  :additional-query "filter(\"code\"<>'11111')",
                  :model-fields ["cities"]}}
          {:model "City",
           :read {:fields ["cityName" "major"],
                  :additional-query
                    "filter(\"major\"<>'Adam West'and\"author\"==<:user-id>)",
                  :model-fields ["country"]}}]}]})

(def _test-app-data-w-relations-v2
  {:objects
     [{:fields
         [{:constraints [:id-constraints], :name "id", :type :id}
          {:constraints [:not-null], :name "dzien", :type :int}
          {:constraints [:not-null], :name "miesiac", :type :int}
          {:constraints [:not-null], :name "godzinaRozpoczecia", :type :int}
          {:constraints [:not-null], :name "liczbaGodzin", :type :int}
          {:name "opis", :type :str2048}],
       :name "WpisKartyPracy",
       :relations [{:name "pracownikId",
                    :references "User",
                    :relation-name-here "pracownik",
                    :relation-name-outside "wpisyPracownika",
                    :relation-type :o2m}]}],
   :serializers [{:allowed-roles ["kadry" "admin"],
                  :name "dlaKadr",
                  :operations
                    [{:create {:fields ["dzien" "miesiac" "godzinaRozpoczecia"
                                        "liczbaGodzin" "opis"]},
                      :delete true,
                      :model "WpisKartyPracy",
                      :read {:fields ["dzien" "miesiac" "godzinaRozpoczecia"
                                      "liczbaGodzin" "pracownikId" "opis"],
                             :model-fields ["pracownik"]},
                      :update true}
                     {:model "User",
                      :model-fields ["wpisyPracownika"],
                      :read {:fields ["username" "id"]}}]}]})

; =============== TEST DATA FOR routes.clj ================ END