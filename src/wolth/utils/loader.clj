(ns wolth.utils.loader
  (:require [io.pedestal.http :as http]
            [wolth.utils.file-helpers :as fh]
            [ring.util.response :as ring-resp]))



(def user-modules (atom {}))

(def standard-interceptors {:json http/json-body, :html http/html-body})

(defn assign-interceptor-func
  [intercep]
  (let [int-symbol (or (get standard-interceptors (first intercep))
                       (get @user-modules (first intercep))
                       (throw (AssertionError.
                                "I don't know what this interceptor is")))
        optional-args (second intercep)]
    (if (fn? int-symbol) (apply int-symbol optional-args) int-symbol)))


(defn object-interceptors
  [interceptors all-intercep]
  (->> interceptors
       (map all-intercep)
       (apply merge)
       (map assign-interceptor-func)
       (into [])
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


;; this should be expanded to support more than just default response value
(defn object-default-route
  [obj-intercep object]
  (or (vector? obj-intercep)
      (throw (RuntimeException. "The obj-intercep should always be a VECTOR!")))
  (conj obj-intercep
        (fn default-resp [r] (ring-resp/response (object :default-data)))))

(comment
  (ring-resp/response {:hello "world"}))

(defn routes-from-object
  [prepared-interceptors single-routes-map prefix]
  [(str prefix (single-routes-map :url-name)) :any
   (object-default-route prepared-interceptors single-routes-map) :route-name
   (keyword
     (or (single-routes-map :name) (single-routes-map :url-name) "undefined"))])

(defn routes-from-map
  [parsed-config prefix]
  (println (str "PARSED" parsed-config))
  (let [interceptors (parsed-config :interceptors)
        object-list (parsed-config :objects)]
    (set (concat (map (fn create-route [obj]
                        (routes-from-object
                          (object-interceptors
                            (cons :all (obj :additional-interceptors))
                            interceptors)
                          obj
                          prefix))
                   object-list)))))


;; TODO: musisz dodać tutaj opcje warunkowego dodawania modułów
;; załóżmy że będzie więcej niż jedna aplikacja korzystająca z tego samego
;; modułu
(defn load-application-modules!
  [config]
  (doall (map (fn load-and-add-module [module-vec]
                (let [mod-path (first module-vec)
                      int-dict (second module-vec)]
                  (load-file mod-path)
                  (swap! user-modules merge int-dict)))
           (get config :modules)))
  config)

(defn create-routes-for-one-application
  [filepath]
  (let [prefix (fh/create-app-name-prefix filepath)]
    (-> filepath
        (fh/get-app-file-content)
        (fh/parsed-app-configuration)
        (load-application-modules!)
        (routes-from-map prefix))))

(defn merge-app-routes [routes] (first routes))

(defn load-everything
  [app-paths]
  (->> app-paths
       (fh/expand-app-paths)
       (filter fh/validate-app-config) ;; this is perfect place to put in Spec!!
       (map #(.getPath %))
       (map create-routes-for-one-application)
       ;;  (merge-app-routes)
  ))