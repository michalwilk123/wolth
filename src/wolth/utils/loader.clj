(ns wolth.utils.loader
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [wolth.utils.spec :refer [test-app-structure]]
            [wolth.db.user :as user]
            [wolth.db.utils :refer [execute-sql-expr!]]
            [next.jdbc :refer [get-datasource]]
            [wolth.db.models :refer [generate-create-table-sql]]
            [wolth.utils.crypto :refer [create-password-hash]]
            [wolth.db.fields :refer [create-uuid]]
            [wolth.server.config :refer [cursor-pool app-data-container]]
            [io.pedestal.log :as log]
            [next.jdbc :as jdbc]))

(def _test-application-path "test/system/person/person.app.edn")

(def _test-app-data
  {:meta {:admin {:name "myAdmin", :password "admin"}, :author "Michal Wilk"},
   :objects [{:fields [{:constraints [:not-null], :name "name", :type :str32}
                       {:name "note", :type :text}],
              :name "Person",
              :options [:uuid-identifier]}
             {:fields [{:name "content", :type :text}],
              :name "Post",
              :options [:uuid-identifier],
              :relations [{:name "author",
                           :ref-type :o2m,
                           :references "Person",
                           :related-name "posts"}]}],
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


(defn throw-loader-exception
  [reason]
  (println {:info reason})
  (throw (ex-info "WolthLoaderException" {:info reason})))

(defn load-application!
  [app-path]
  (-> app-path
      (slurp)
      (read-string)))

(comment
  (load-application! _test-application-path))

; tutaj odpalam spec
(defn test-application-file!
  [app-path]
  (let [file-obj (io/file app-path)]
    (cond
      (not (.exists file-obj))
        (throw-loader-exception (format "Could not find the file: %s" app-path))
      (not (.canRead file-obj))
        (throw-loader-exception
          (format
            "Could not load file: %s because current user has no read permission"
            app-path)))
    (test-app-structure (slurp file-obj)))
  (log/info ::test-application-file!
            (format "File %s loaded without errors" app-path)))

(comment
  (test-application-file! _test-application-path)
  (test-application-file! "dasdnsjadkjsan")
  (test-application-file! "logs/tfile.txt"))

(defn load-user-functionalities
  [app-data]
  (-> app-data
      (update-in [:objects] #(conj % user/user-table user/token-table))
      (update-in [:serializers]
                 #(conj % user/user-admin-view user/user-regular-view))))

(comment
  (clojure.pprint/pprint (load-user-functionalities _test-app-data)))

(defn store-applications!
  [app-names app-datas]
  (let [app-tuples (zipmap app-names app-datas)]
    (reset! app-data-container app-tuples)
    (log/info ::store-applications!
              (format "Stored app config for the applications: %s"
                      (str/join ", " app-names)))))

(comment
  (store-applications! '("test-app") (list _test-app-data))
  (deref app-data-container))


(defn store-db-connections!
  [app-names app-datas]
  (let [cursors (zipmap app-names
                        (map #(get-datasource (get % :persistent-db))
                          app-datas))]
    (reset! cursor-pool cursors)
    (log/info ::store-db-connections!
              (format "Stored connections for the applications: %s"
                      (str/join ", " app-names)))))

(comment
  (store-db-connections! '("test-app1") (list _test-app-data))
  (deref cursor-pool))


(defn store-routes! [routes])

(defn create-sql-tables!
  [app-names applications]
  (let [table-sql (map (fn [x] (generate-create-table-sql (get x :objects)))
                    applications)]
    (run! (fn [[app-name tables]]
            (run! (partial execute-sql-expr! app-name) tables))
          (zipmap app-names table-sql))))

(comment
  (create-sql-tables! '("test-app1") (list _test-app-data)))

(defn admin-account-exists?
  [app-name admin-name]
  (some? (seq (execute-sql-expr! app-name
                                 ["SELECT 1 FROM User WHERE USERNAME = ?;"
                                  admin-name]))))

(comment
  (admin-account-exists? "person" "myAdmin")
  (admin-account-exists? "person" "admin ktory nie istnieje"))

(defn create-admin-account!
  [app-name app-data]
  (assert (string? app-name))
  (assert (map? app-data))
  (let [admin-cred (get-in app-data [:meta :admin])
        admin-name (get admin-cred :name)]
    (if (admin-account-exists? app-name admin-name)
      (log/info
        ::create-admin-account!
        (format
          "Admin with name: %s already exists. Skipping this step for app: %s"
          admin-name
          app-name))
      (let
        [hashed-admin-pass (create-password-hash (get admin-cred :password))
         generated-id (create-uuid)
         sql-expr
           ["INSERT INTO User (USERNAME, PASSWORD, ROLE, ID) VALUES (?, ?, ?, ?);"
            admin-name hashed-admin-pass "admin" generated-id]]
        (execute-sql-expr! app-name sql-expr)
        (log/info
          ::create-admin-account!
          (format
            "Application %s did not had the admin account set. Creating one for the %s app"
            admin-name
            app-name))))))

(comment
  (create-admin-account! "person" _test-app-data)
  (execute-sql-expr! "person"
                     ["DELETE FROM User WHERE USERNAME = ?" "myAdmin"]))