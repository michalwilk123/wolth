(ns wolth.utils.user-input)


(defn user-config-valid? [] nil)


(defn app-file-content
  [filepath]
  (println "Looking for file..")
  (try (slurp filepath)
       (catch java.io.FileNotFoundException ex
         (.printStackTrace ex)
         (str "I could not file your app configuration" (.getMessage ex)))))


; gives from string either map or nil if the format is incorrenct
(defn parsed-app-configuration [file-content]
  (println "Parsing the configuration")
  (clojure.edn/read-string file-content))


(def objects-location-path [:objects])


(defn routes-object-for-single-application
  [filepath]
  (-> filepath
      (app-file-content)
      (parsed-app-configuration)
      (get-in objects-location-path)))



(defn merged-routes-object-from-all-applications [directories] nil)


(comment (slurp "dsadsad"))
