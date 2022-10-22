(ns wolth.server.-test-data)


; =============== TEST DATA FOR utils.clj ================ START

(def _server_test_app_data
  {:objects
     [{:fields [{:constraints [:not-null], :name "name", :type :str32}
                {:name "note", :type :text}],
       :name "Person",
       :options [:uuid-identifier]}
      {:name "User",
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
                      :delete true}
                     {:model "Person", :create {:fields ["note" "name"]}}]}]})
; =============== TEST DATA FOR utils.clj ================ END

; =============== TEST DATA FOR serializers.clj ================ START
(def _test-serializer-spec
  {:name "public",
   :allowed-roles true,
   :operations [{:model "User",
                 :read {:fields ["email" "username" "id"], :attached []},
                 :update {:fields ["username" "email"]},
                 :create {:fields ["username" "email" "password"],
                          :attached [["role" "regular"]]},
                 :delete true}]})

(def _test-object-spec
  '({:name "User",
     :fields
       [{:constraints [:not-null :unique], :name "username", :type :str128}
        {:constraints [:not-null], :name "password", :type :password}
        {:constraints [:not-null], :name "role", :type :str128}
        {:constraints [:unique], :name "email", :type :str128}],
     :options [:uuid-identifier]}))

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
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {},
             :uri "/test-app/User/*/public",
             :path-params {:User_query "*"},
             :request-method :get}})

(def _test-patch-request-map
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {:username "Marek"},
             :uri "/test-app/User/username==Przemek/public",
             :path-params {:User_query "username==Przemek"},
             :request-method :patch}})

(def _test-delete-request-map
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {},
             :uri "/test-app/User/*/public",
             :path-params {:User_query "username==Przemek"},
             :request-method :delete}})

(def _test-bank-request-map
  {:logged-user {:username "Przemek", :id 22222},
   :request {:json-params {},
             :uri "/test-app/User/*/public",
             :query-params {:num 50},
             :request-method :delete}})

(def _serializers_test_app_data
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
                  :operations [{:model "User",
                                :read {:fields ["username" "email"],
                                       :filter [:= "author" :user-id]},
                                :update {:fields ["username" "email"]},
                                :create {:fields ["username" "email"
                                                  "password"],
                                         :attached [["role" "regular"]]},
                                :delete true}]}]})

; =============== TEST DATA FOR serializers.clj ================ END