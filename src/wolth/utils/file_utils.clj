(ns wolth.utils.file-utils
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http :as http]))


(defn user-config-valid? [] nil)

(def user-modules (atom {}))

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
  [intercep]
  (let [int-symbol (or (get standard-interceptors (first intercep))
                       (get @user-modules (first intercep))
                       (throw (AssertionError.
                                "I don't know what this interceptor is")))
        optional-args (second intercep)]
    (if (fn? int-symbol) (apply int-symbol optional-args) int-symbol)))


; merge default (:all) interceptors with the custom ones
; defined in :additional-interceptors parameter
(defn object-interceptors-deprecated
  [all-interceptors obj-interceptors]
  (into []
        (merge (all-interceptors :all)
               (flatten (map #(->> %
                                   (get all-interceptors)
                                   (assign-interceptor-func))
                          (obj-interceptors :additional-interceptors))))))


(defn object-interceptors
  [interceptors all-intercep]
  (->> interceptors
       (map all-intercep)
       (apply merge)
       (map assign-interceptor-func)
       ;; (assign-inter-fn)
       ;; (construct-interceptor)
       ;; (map (fn construct-interceptor [single-inter-fn]
       ;;        (cond
       ;;          (vector? single-inter) (apply)
       ;;          (nil? single-inter) :else
       ;;          (throw
       ;;            (ex-info
       ;;              "The second arg of intercep map should be vector or
       ;;              nil")))))
  ))


(defn object-default-route
  [obj-intercep object]
  (conj obj-intercep
        (fn default-resp [r] (ring-resp/response "oooooooooooooooo"))))
        ;; (fn default-resp [r] (ring-resp/response (object :default-data)))))


(defn routes-from-object
  [prepared-interceptors single-routes-map]
  [(str "/" (single-routes-map :url-name)) :any
   (object-default-route prepared-interceptors single-routes-map) :route-name
   (or (single-routes-map :name) (single-routes-map :url-name) "undefined")])


;; (defn routes-from-map
;;   [parsed-config]
;;   (let [interceptors (parsed-config :interceptors)
;;         object-list (parsed-config :objects)]
;;     (object-interceptors
;;       (into [] (cons :all ((first object-list) :additional-interceptors)))
;;       interceptors)))


(defn routes-from-map
  [parsed-config]
  (let [interceptors (parsed-config :interceptors)
        object-list (parsed-config :objects)]
    (set (concat (map (fn create-route [obj]
                        (routes-from-object
                          (object-interceptors
                            (cons :all (obj :additional-interceptors))
                            interceptors)
                          obj))
                   object-list)))))


(defn load-application-modules!
  [config]
  (doall (map (fn load-and-add-module [module-vec]
                (let [mod-path (first module-vec)
                      int-dict (second module-vec)]
                  (load-file mod-path)
                  (swap! user-modules merge int-dict)))
           (get config :modules)))
  config)

(defn routes-object-for-single-application
  [filepath]
  (-> filepath
      (app-file-content)
      (parsed-app-configuration)
      (load-application-modules!)
      (routes-from-map)))

(defn merged-routes-object-from-all-applications [directories] nil)

;; REST IN PEPPERONIS
;; (defn assign-interceptor-func
;;   [inter]
;;   (map (fn build-interceptor [inter]
;;          (->> inter
;;               (fn find-function-symbol [inter]
;;                 (or (get standard-interceptors kword)
;;                     (get @user-modules kword)
;;                     (throw (AssertionError.
;;                              "I don't know what this interceptor is"))))))))

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
