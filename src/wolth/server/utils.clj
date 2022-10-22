(ns wolth.server.utils
  (:require [clojure.string :as str]
            [wolth.server.config :refer [app-data-container]]
            [wolth.server.-test-data :refer [_server_test_app_data]]
            [wolth.server.exceptions :refer
             [def-interceptor-fn throw-wolth-exception]]
            [wolth.utils.common :as common]))

(def operations-lut
  {:post :create, :delete :delete, :get :read, :patch :update})

(defn get-associated-app-data!
  [app-name]
  (if-let [app-data (get @app-data-container app-name)]
    app-data
    (throw-wolth-exception :404 (format "Application %s not found" app-name))))

(comment
  (get-associated-app-data! "app"))


(defn get-associated-objects
  [app-data-objects table-names]
  (map (fn [tab-name]
         (common/find-first #(= (get % :name) tab-name) app-data-objects))
    table-names))

(comment
  (get-associated-objects (_server_test_app_data :objects) (list "User" "Person"))
  (get-associated-objects (_server_test_app_data :objects) (list "Person" "User")))


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
                 :patch (list (nth splitted-names 2))
                 :bank nil)]
    [app-name serializer-name tables]))

(comment
  (uri->parsed-info "/app/Person/public" :post)
  (uri->parsed-info "/person/User/user-regular" :post)
  (uri->parsed-info "/app/Person/firstquery/NextTable/*/public" :get)
  (uri->parsed-info "/app/getPrimes" :bank))

(defn uri->app-name [uri] (second (str/split uri #"/")))

(comment
  (uri->app-name "/app/Person/public")
  (uri->app-name "/app/Person"))

;; (def-interceptor-fn utility-interceptor-fn [ctx] (assoc ctx :appname
;; (uri->app-name (ctx :uri))))
(def-interceptor-fn utility-interceptor-fn
                    [ctx]
                    (assoc ctx
                      :app-name (uri->app-name (get-in ctx [:request :uri]))))

(def utility-interceptor
  {:name ::WOLTH-UTILITY-INTERCEPTOR, :enter utility-interceptor-fn})

(defn app-path->app-name
  [path]
  (-> path
      (str/split #"/")
      (last)
      (str/split #"\.")
      (first)))

(comment
  (app-path->app-name "test/system/person/person.app.edn"))

(defn get-related-serializer-spec
  [objects serializer-operations method]
  (as-> objects it
    (map :name it) ; get object name
    ; find right serializers
    (map (fn [tab-name]
           (common/find-first #(= tab-name (:model %)) serializer-operations))
      it)
    ; assign appropriate operation method
    (map (operations-lut method) it)))

(comment
  (get-related-serializer-spec
    (get-associated-objects (_server_test_app_data :objects) (list "User" "Person"))
    (get-in _server_test_app_data [:serializers 0 :operations])
    :post)
  (get-related-serializer-spec
    (get-associated-objects (_server_test_app_data :objects) (list "User"))
    (get-in _server_test_app_data [:serializers 0 :operations])
    :delete))
