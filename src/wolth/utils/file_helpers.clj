(ns wolth.utils.file-helpers
  (:require clojure.edn [clojure.string :as str]))


(defn get-app-file-content
  [filepath]
  (try (slurp filepath)
       (catch java.io.FileNotFoundException ex
         (.printStackTrace ex)
         (str "I could not file your app configuration" (.getMessage ex)))))


; gives from string either map or nil if the format is incorrenct
(defn parsed-app-configuration
  [file-content]
  (clojure.edn/read-string file-content))

(def ignore-app-name-preffix "__")

(defn get-name-from-filepath
  [filepath]
  (-> filepath
      (str/split (re-pattern (java.io.File/separator)))
      (last)
      (str/replace #"\.app\.(edn)?" "")))

(defn create-app-name-prefix
  [filepath]
  (let [app-name (get-name-from-filepath filepath)]
    (if (str/starts-with? app-name ignore-app-name-preffix) "" app-name)))

(defn flatten-nested-routes [filepaths] nil)

(defn create-routes-from-fpaths
  [filepaths]
  (-> filepaths
      ;; (flatten-nested-routes)
      ))

(defn expand-single-app-path [app-path]
  )

(defn expand-app-paths [app-path])

(defn validate-app-config [app-path] true)

(comment
  (-> "dsadas dsa dsa d sa"
      (str/split (re-pattern (java.io.File/separator)))
      (last))
  (get-name-from-filepath "test/system/hello_world/apps/hello-world.app.edn"))