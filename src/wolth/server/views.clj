(ns wolth.server.views
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [io.pedestal.log :as log]
            [wolth.server.exceptions :refer [def-interceptor-fn]]
            [wolth.utils.common :refer [clob-to-string]]
            [wolth.utils.misc :refer
             [unflat-end-condition unflat-nested-struct]]))

(defn translate-to-readable-form
  [val]
  (cond (instance? java.sql.Clob val) (clob-to-string val)
        (instance? org.h2.api.TimestampWithTimeZone val) (.toString val)
        :else val))

(comment
  (translate-to-readable-form 1))

(defn sql-map->map
  [nmap]
  (log/info ::sql-map->map nmap)
  (into {}
        (map (fn [[k val]]
               [(-> k
                    (name)
                    (str/split #"/")
                    (last)
                    (str/lower-case)
                    (keyword)) (translate-to-readable-form val)])
          nmap)))


(comment
  (sql-map->map #:USER{:ID "440b753d-1928-4007-bcd5-392ef5b3d0e7",
                 :USERNAME "admin",
                 :PASSWORD "haslo",
                 :ROLE "admin",
                 :EMAIL nil}))



(defn parse-multitable-sql-result
  [sql-map]
  (letfn [(get-tab-name [key]
            (-> key
                (str)
                (str/split #"\/")
                (first)
                (subs 1)))]
    (reduce
      (fn [acc [k value]]
        (update acc
                (get-tab-name k)
                (fn [m]
                  (assoc m
                    (-> k
                        (name)
                        (str/lower-case)
                        (keyword))
                      (translate-to-readable-form value)))))
      {}
      sql-map)))

(comment
  (parse-multitable-sql-result {:WRITER/NAME "writer3",
                                :WRITER/NOTE "Testowa notatka",
                                :WRITER/ID 3,
                                :POST/ID 3,
                                :POST/AUTHOR 3,
                                :POST/CONTENT "pierwszy post"}))

(defn squash-maps
  [maps table-names]
  (unflat-nested-struct maps table-names unflat-end-condition))

(comment
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}}
                {"tab1" {:aa 2}, "tab2" {:dd "gg"}}]
               (list "tab1" "tab2"))
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}}
                {"tab1" {:aa nil}, "tab2" {:dd "gg"}}]
               (list "tab1" "tab2"))
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}, "tab3" {:qq 1}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}, "tab3" {:qq 2}}
                {"tab1" {:aa 2}, "tab2" {:dd "gg"}, "tab3" {:qq 3}}]
               (list "tab1" "tab2" "tab3"))
  (squash-maps [{"tab1" {:aa 1}, "tab2" {:dd "bb"}, "tab3" {:qq 1}}
                {"tab1" {:aa 2}, "tab2" {:dd "aa"}, "tab3" {:qq nil}}
                {"tab1" {:aa 2}, "tab2" {:dd "cc"}, "tab3" {:qq nil}}
                {"tab1" {:aa 3}, "tab2" {:dd nil}, "tab3" {:qq nil}}]
               (list "tab1" "tab2" "tab3"))
  (squash-maps [{"tab1" {:aa 1}} {"tab1" {:aa 2}} {"tab1" {:aa 2}}]
               (list "tab1")))

(defn join-queries-with-fields
  [query-structure fields-to-inject]
  (if (empty? fields-to-inject)
    query-structure
    (reduce (fn [acc [k value]]
              (cons (assoc k
                      (first fields-to-inject) (join-queries-with-fields
                                                 value
                                                 (rest fields-to-inject)))
                    acc))
      nil
      query-structure)))

(comment
  (join-queries-with-fields '{{:aa 1} ({:dd "bb"}),
                              {:aa 2} ({:dd "gg"} {:dd "aa"})}
                            (list "tab1-items"))
  (join-queries-with-fields '{{:aa 1} {{:dd "bb"} ({:qq 1})},
                              {:aa 2} {{:dd "gg"} ({:qq 3}),
                                       {:dd "aa"} ({:qq 2})}}
                            (list "tab1-items" "tab2-items")))

(defn create-nested-sql-result
  [parsed-result tables relation-fields]
  (let [uppercased-table-names (map str/upper-case tables)]
    (as-> parsed-result it
      (map parse-multitable-sql-result it)
      (squash-maps it uppercased-table-names)
      (join-queries-with-fields it relation-fields))))

(comment
  (create-nested-sql-result [{:WRITER/NAME "writer3",
                              :WRITER/NOTE "Testowa notatka",
                              :WRITER/ID 3,
                              :POST/ID 3,
                              :POST/AUTHOR 3,
                              :POST/CONTENT "pierwszy post"}
                             {:WRITER/NAME "writer2",
                              :WRITER/NOTE "notatka 123",
                              :WRITER/ID 3,
                              :POST/ID nil,
                              :POST/AUTHOR nil,
                              :POST/CONTENT nil}
                             {:WRITER/NAME "writer1",
                              :WRITER/NOTE "lorem ipsum",
                              :WRITER/ID 3,
                              :POST/ID 2,
                              :POST/AUTHOR 3,
                              :POST/CONTENT "drugi post"}]
                            (list "Writer" "Post")
                            (list "posts")))

(defn create-model-view
  [ctx]
  (let [result (get ctx :model-result)
        join-fields (get ctx :relation-fields)
        table-names (get ctx :table-names)
        request-method (get-in ctx [:request :request-method])
        response (case request-method
                   :get {:status 200,
                         :body (create-nested-sql-result result
                                                         table-names
                                                         join-fields)}
                   :post {:status 201, :body {:message "Created data"}}
                   :patch {:status 200, :body {:message "Updated data"}}
                   :delete {:status 200, :body {:message "Deleted data"}})]
    (assoc ctx :response response)))

(s/def ::body
  (s/or :a map?
        :b vector?
        :c number?
        :d string?
        :e nil?))

(s/def ::status (s/int-in 100 600))

(s/def ::result-object (s/and map? (s/keys :req-un [::body ::status])))

(comment
  (s/valid? ::result-object {:status 200, :body "OK"})
  (s/valid? ::result-object "OKEJ")
  (s/explain ::result-object "OKEJ")
  (s/valid? ::result-object {:status 1000, :body (fn [x] x)})
  (s/explain ::result-object {:status 1000, :body (fn [x] x)}))

(defn create-bank-view
  [ctx]
  (let [result (ctx :bank-result)]
    (if (s/valid? ::result-object result)
      (assoc ctx :response result)
      (assoc ctx :response {:status 200, :body result}))))

(def-interceptor-fn view-interceptor-fn
                    [ctx]
                    (cond (get ctx :bank-result) (create-bank-view ctx)
                          (get ctx :model-result) (create-model-view ctx)
                          :else (assoc ctx :response {:status 204, :body nil})))


(def wolth-view-interceptor
  {:name ::VIEW-INTERCEPTOR, :enter view-interceptor-fn})

