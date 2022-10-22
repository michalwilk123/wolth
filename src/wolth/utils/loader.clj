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
            [wolth.utils.-test-data :refer
             [_test_application_path _loader_test_app_data]]
            [wolth.server.config :refer
             [cursor-pool app-data-container bank-namespaces]]
            [io.pedestal.log :as log]))

(def WOLTH-GENERATED-NS-PREFIX "--funcs")

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
  (load-application! _test_application_path))

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
  (test-application-file! _test_application_path)
  (test-application-file! "dasdnsjadkjsan")
  (test-application-file! "logs/tfile.txt"))

(defn load-user-functionalities
  [app-data]
  (-> app-data
      (update-in [:objects] #(conj % user/user-table user/token-table))
      (update-in [:serializers]
                 #(conj % user/user-admin-view user/user-regular-view))))

(comment
  (clojure.pprint/pprint (load-user-functionalities _loader_test_app_data)))

(defn store-applications!
  [app-names app-datas]
  (let [app-tuples (zipmap app-names app-datas)]
    (reset! app-data-container app-tuples)
    (log/info ::store-applications!
              (format "Stored app config for the applications: %s"
                      (str/join ", " app-names)))))

(comment
  (store-applications! '("test-app") (list _loader_test_app_data))
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
  (store-db-connections! '("test-app1") (list _loader_test_app_data))
  (deref cursor-pool))


(defn create-sql-tables!
  [app-names applications]
  (let [table-sql (map (fn [x] (generate-create-table-sql (get x :objects)))
                    applications)]
    (run! (fn [[app-name tables]]
            (run! (partial execute-sql-expr! app-name) tables))
          (zipmap app-names table-sql))))

(comment
  (create-sql-tables! '("test-app1") (list _loader_test_app_data)))

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
  (create-admin-account! "person" _loader_test_app_data)
  (execute-sql-expr! "person"
                     ["DELETE FROM User WHERE USERNAME = ?" "myAdmin"]))

(defn- create-namespaces!
  [app-names]
  (reset! bank-namespaces {})
  (run!
    (fn [single-app-name]
      (let [str-namespace (str WOLTH-GENERATED-NS-PREFIX "-" single-app-name)
            sym-namespace (symbol str-namespace)]
        (swap! bank-namespaces assoc
          single-app-name
          (create-ns sym-namespace))))
    app-names))

(comment
  (create-namespaces! (list "application1" "app2"))
  @bank-namespaces
  (reset! bank-namespaces {}))

(defn- load-under-ns!
  [app-namespace symbols-to-load result-names filename]
  (assert (= (count symbols-to-load) (count result-names)))
  (letfn [(load-function-under-ns [sym-to-load name-to-load]
            (intern app-namespace
                    (symbol name-to-load)
                    (eval (symbol sym-to-load))))]
    (load-file filename) (run! (partial apply load-function-under-ns)
                               (zipmap symbols-to-load result-names))))

(comment
  (load-under-ns! (create-ns 'abc)
                  (list "nPrimes2")
                  (list "primes")
                  "functions/clojureFunction.clj"))

(defn- fetch-function-data
  [func-data]
  (vals (select-keys func-data [:path :function-name :name])))

(comment
  (fetch-function-data (first (_loader_test_app_data :functions))))

(defn- load-app-functions!
  [func-ns functions]
  (log/info ::load-app-functions!
            (str "Loading functions under namespace: " (ns-name func-ns)))
  (let [-func-map
          (reduce (fn [accum el]
                    (let [[path sym name] (fetch-function-data el)
                          vec-to-insert (vector sym name)]
                      (update-in accum [path] (partial cons vec-to-insert))))
            {}
            functions)
        func-map (reduce-kv (fn [acc k val]
                              (assoc acc
                                k {:sym-names (map first val),
                                   :f-names (map second val)}))
                            {}
                            -func-map)]
    (run! (fn [[fpath val]]
            (load-under-ns! func-ns (val :sym-names) (val :f-names) fpath))
          func-map)))

(comment
  (load-app-functions! (create-ns 'aaa) (_loader_test_app_data :functions)))

(defn load-bank-functions!
  [app-names app-datas]
  (create-namespaces! app-names)
  (let [app-functions (map :functions app-datas)
        app-namespaces (map @bank-namespaces app-names)]
    (run! (partial apply load-app-functions!)
          (zipmap app-namespaces app-functions))))

(comment
  (load-bank-functions! (list "test-app1") (list _loader_test_app_data)))
