(ns wolth.server.utils
  (:require [clojure.string :as str]
            [wolth.utils.common :as common]))

(def _test-app-data
  {:objects
     [{:name "User",
       :fields
         [{:constraints [:not-null :unique], :name "username", :type :str128}
          {:constraints [:not-null], :name "password", :type :password}
          {:constraints [:not-null], :name "role", :type :str128}
          {:constraints [:unique], :name "email", :type :str128}],
       :options [:uuid-identifier]}],
   :serializers [{:name "public",
                  :model "User",
                  :read {:fields ["author" "content" "id"], :attached []},
                  :update {:fields ["username" "email"]},
                  :create {:fields ["username" "email" "password"],
                           :attached [["role" "regular"]]},
                  :delete true}]})

(defn get-associated-app-data! [app-name] _test-app-data)

(defn get-associated-objects
  [app-data table-names]
  (filter #(.contains table-names (% :name)) (app-data :objects)))

(comment
  (get-associated-objects _test-app-data (list "User")))

(defn get-serializer-data
  [app-data serializer-name]
  (first (filter #(= (% :name) serializer-name) (app-data :serializers))))

(comment
  (get-serializer-data _test-app-data "public"))

(defn uri->parsed-info
  [uri method]
  (let [splitted-names (str/split uri #"/")
        app-name (second splitted-names)
        serializer-name (last splitted-names)
        tables (case method
                 :get (->> splitted-names
                           (drop-last)
                           (drop 2)
                           (take-nth 2))
                 :post (list (nth splitted-names 2))
                 :delete (list (nth splitted-names 2))
                 :patch (list (nth splitted-names 2)))]
    [app-name serializer-name tables]))

(comment
  (uri->parsed-info "/app/Person/public" :post)
  (uri->parsed-info "/app/Person/firstquery/NextTable/*/public" :get))