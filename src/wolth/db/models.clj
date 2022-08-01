(ns wolth.db.models
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [honey.sql.helpers :as hsql]))

(def db {:dbtype "h2" :dbname "example"})

(def ds (jdbc/get-datasource db))

(def sqlmap {:select [:a :b :c]
             :from   [:foo]
             :where  [:= :foo.a "baz"]})

;; (jdbc/execute! ds ["
;; create table address (
;;   id int auto_increment primary key,
;;   name varchar(32),
;;   email varchar(255)
;; )"])


(comment
  (sql/format sqlmap)
  (hsql/create-table :if-not-exists {})
  )
