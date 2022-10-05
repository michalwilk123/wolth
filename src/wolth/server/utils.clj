(ns wolth.server.utils
  (:require [clojure.string :as str]
            [wolth.server.config :refer [app-data-container]]
            [wolth.server.exceptions :refer [throw-wolth-exception]]
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

(defn get-associated-app-data!
  [app-name]
  (if-let [app-data (get @app-data-container app-name)]
    app-data
    (throw-wolth-exception :404 (format "Application %s not found" app-name))))

(comment
  (get-associated-app-data! "app"))

(defn get-associated-objects
  [app-data table-names]
  (filter #(.contains table-names (% :name)) (app-data :objects)))

(comment
  (get-associated-objects _test-app-data (list "User")))


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

(defn uri->app-name [uri] (second (str/split uri #"/")))

(comment
  (uri->app-name "/app/Person/public"))

(defn utility-context [ctx] (merge ctx {:app-name (uri->app-name (ctx :uri))}))

(def utility-interceptor
  {:name ::UTILITY-INTERCEPTOR, :enter utility-context})