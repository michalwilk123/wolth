(ns wolth.utils.spec
  (:require [clojure.spec.alpha :as s]
            [wolth.db.fields :refer [value-normalization-lut]]
            [wolth.server.utils :refer [operations-lut]]
            [wolth.utils.common :as help]
            [wolth.server.bank :refer [normalizers]]
            [clojure.pprint :refer [pprint]]
            [wolth.utils.loader :refer [load-application!]]))


(s/def ::model string?)
(s/def ::name string?)
(s/def ::path string?)
(s/def ::function-name string?)
(s/def ::password string?)
(s/def ::author string?)
(s/def ::type (partial contains? help/field-lut))
(s/def ::fn-type (partial contains? normalizers))
(s/def ::constraints
  (s/coll-of (partial contains? help/constraints-lut)
             :kind vector?
             :distinct true))
(s/def ::relation-type
  (partial help/vector-contains? help/availiable-relationships))
(s/def ::references string?)
(s/def ::dbname string?)
(s/def ::dbtype string?)
(s/def ::arg-source
  (s/or :query-source (partial = :query)
        :body-source (partial = :body)))
(s/def ::additional-query string?)
(s/def ::model-fields (s/coll-of string? :distinct true :kind vector?))


(s/def ::method (partial contains? operations-lut))
(s/def ::arguments
  (s/coll-of (s/tuple string? ::fn-type) :distinct true :kind vector?))
(s/def ::attached
  (s/coll-of (s/tuple
               string?
               (s/or :function (partial contains? value-normalization-lut)
                     :string string?
                     :number number?
                     :boolean boolean?))
             :distinct true
             :kind vector?))
(s/def ::allowed-roles
  (s/or :binary boolean?
        :specified-roles (s/coll-of string?)))
(s/def ::wolth-table-field
  (s/keys :req-un [::name ::type] :opt-un [::constraints]))
(s/def ::wolth-table-relation
  (s/keys :req-un [::name ::relation-type ::references]))



(s/def ::fields
  (s/or :serializer (s/coll-of string? :distinct true :kind vector?)
        :model (s/coll-of ::wolth-table-field :distinct true :kind vector?)))
(s/def ::relations
  (s/coll-of ::wolth-table-relation :distinct true :kind vector?))

(s/def ::wolth-operation-primitive
  (s/or :binary boolean?
        :configured (s/keys :opt-un [::fields ::attached ::additional-query
                                     ::model-fields])))


(s/def ::create ::wolth-operation-primitive)
(s/def ::delete ::wolth-operation-primitive)
(s/def ::read ::wolth-operation-primitive)
(s/def ::update ::wolth-operation-primitive)


(s/def ::admin (s/keys :req-un [::name ::password]))
(s/def ::wolth-operation
  (s/keys :req-un [::model] :opt-un [::create ::read ::delete ::update]))


(s/def ::operations (s/coll-of ::wolth-operation :kind vector? :distinct true))
(s/def ::wolth-object (s/keys :req-un [::fields ::name] :opt-un [::relations]))
(s/def ::wolth-function
  (s/keys :req-un [::allowed-roles ::arg-source ::arguments ::function-name
                   ::method ::name ::path]))
(s/def ::wolth-serializer
  (s/keys :req-un [::allowed-roles ::name ::operations]))


(s/def ::objects (s/coll-of ::wolth-object :kind vector? :distinct true))
(s/def ::meta (s/keys :req-un [::admin] :opt-un [::author]))
(s/def ::database-configuration (s/keys :req-un [::dbname ::dbtype]))
(s/def ::functions (s/coll-of ::wolth-function :kind vector? :distinct true))
(s/def ::serializers
  (s/coll-of ::wolth-serializer :kind vector? :distinct true))



(def _person-application-path "test/system/person/person.app.edn")
(def _todo-application-path "test/system/todo/todo.app.edn")
(def _hello_application-path "test/system/hello.app.edn")

(comment
  (def _person-app-data (load-application! _person-application-path))
  (def _todo-app-data (load-application! _todo-application-path))
  (def _hello-app-data (load-application! _hello_application-path))
  (s/explain ::meta (_todo-app-data :meta))
  (s/explain ::meta (_person-app-data :meta))
  (s/explain ::serializers (_person-app-data :serializers))
  (s/explain ::serializers (_todo-app-data :serializers))
  (s/explain ::functions (_person-app-data :functions))
  (s/explain ::database-configuration
             (_person-app-data :database-configuration))
  (s/explain ::database-configuration (_todo-app-data :database-configuration))
  (s/explain ::objects (_todo-app-data :objects))
  (s/explain ::objects (_person-app-data :objects)) ; this is for debug / user
                                                    ; messages
  (s/valid? ::objects (_person-app-data :objects)) ; this is for validation
                                                   ; (outputs boolean)
)

(s/def ::wolth-app-config
  (s/keys :req-un [::objects ::meta ::database-configuration ::serializers]
          :opt-un [::functions]))


(defn wolth-config-valid? [app-data] (s/valid? ::wolth-app-config app-data))

(defn explain-wolth-spec
  [app-data]
  (if-let [problems (some->> app-data
                             (s/explain-data ::wolth-app-config)
                             (:clojure.spec.alpha/problems))]
    (pprint problems)
    (println "OK")))

(comment
  (wolth-config-valid? _todo-app-data)
  (wolth-config-valid? _person-app-data)
  (wolth-config-valid? _hello-app-data)
  (explain-wolth-spec _hello-app-data)
  (explain-wolth-spec _todo-app-data)
  (explain-wolth-spec _person-app-data))