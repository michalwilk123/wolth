(ns wolth.utils.file-utils
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http :as http]))


(defn user-config-valid? [] nil)


(defn app-file-content
  [filepath]
  (try (slurp filepath)
       (catch java.io.FileNotFoundException ex
         (.printStackTrace ex)
         (str "I could not file your app configuration" (.getMessage ex)))))


; gives from string either map or nil if the format is incorrenct
(defn parsed-app-configuration
  [file-content]
  (clojure.edn/read-string file-content))


(def standard-interceptors {:json http/json-body, :html http/html-body})

(defn assign-interceptor-func
  [intercep-kwords]
  (map #((or (standard-interceptors %)
             (%) ;; TODO: zmien to!! Powinno zwracać błąd / ostrzeżenie
             (throw (ex-info "I don't know what this interceptor is" {}))))
    intercep-kwords))


; merge default (:all) interceptors with the custom ones
; defined in :additional-interceptors parameter
(defn object-interceptors
  [all-interceptors obj-interceptors]
  (into []
        (concat (all-interceptors :all)
                (flatten (map #(->> %
                                    (get all-interceptors)
                                    (assign-interceptor-func))
                           (obj-interceptors :additional-interceptors))))))

(defn object-default-route
  [obj-intercep object]
  (conj obj-intercep
        (fn default-resp [r] (ring-resp/response (object :default-data)))))

(defn routes-from-object
  [interceptors single-routes-map]
  [(str "/" (single-routes-map :url-name)) :any
   (object-default-route interceptors single-routes-map) :route-name
   (or (single-routes-map :name) (single-routes-map :url-name) "undefined")])


(defn routes-from-map
  [parsed-config]
  (let [interceptors (parsed-config :interceptors)
        object-list (parsed-config :objects)]
    (set
      (concat (map #(routes-from-object (object-interceptors interceptors %) %)
                object-list)
              []))))

;; (defn routes-from-map
;;   [single-routes-map]
;;   (let [interceptors ( single-routes-map :interceptors)
;;         object-list (single-routes-map :objects)]
;;     (reduce (fn [accumulator rt]
;;               (assoc accumulator
;;                 (rt :url-name) ; key of the routes entry
;;                 {:get (fn [req] (ring-resp/response (rt :default-data))) ;
;;                 default behaviour of get
;;                  :interceptors (object-interceptors interceptors rt)})
;;               {}
;;               single-routes-map))))


(defn routes-object-for-single-application
  [filepath]
  (-> filepath
      (app-file-content)
      (parsed-app-configuration)
      (routes-from-map)))

(defn merged-routes-object-from-all-applications [directories] nil)
