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