(ns wolth.utils.file-helpers
  (:require clojure.edn
            [clojure.string :as str]
            [clojure.java.io :as io]))


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

(def ignore-app-name-preffix "_")

(defn get-name-from-filepath
  [filepath]
  (-> filepath
      (str/split (re-pattern (java.io.File/separator)))
      (last)
      (str/replace #"\.app\.(edn)?" "")))

; is given file object an app
(defn file-obj-app?
  [f]
  ((every-pred #(.exists %)
               (complement #(.isDirectory %))
               #(re-find #"\.app\.(edn)?$" (.getName %)))
    f))

(comment
  (file-obj-app? (clojure.java.io/file "dsajdjkasbds"))
  (file-obj-app? (clojure.java.io/file
                   "test/system/hello_world/_hello-world.app.edn"))
  (file-obj-app? (clojure.java.io/file "test/system/two_apps/test.app")))

; create from filepath prefix to the route object
(defn create-app-name-prefix
  [filepath]
  (assert (string? filepath))
  (->> filepath
       ((fn split-fname [fpath]
          (str/split fpath (re-pattern (java.io.File/separator)))))
       (filter #(re-find #"\.app(\.edn)?$" %)) ; filter out only files with
                                               ; correct extension
       (remove #(str/starts-with? % ignore-app-name-preffix)) ; remove ignored
                                                              ; names
       (map #(str/replace % #"\.app(\.edn)?" "")) ; remove file extensions
       (str/join "/") ; join all names with a slash
       ((fn surround-with-slashes [names]
          (if (str/blank? names) "/" (str "/" names "/")))))) ; prepend name with slash


(comment
  (create-app-name-prefix "test/system/hello_world/_hello-world.app.edn")
  (create-app-name-prefix "test/system/two_apps/test.app/second.app.edn")
  (create-app-name-prefix "test/system/two_apps/test.app/first.app.edn")
  ;; ((fn filter-fnames [fname] ( filter #(re-find #"\.app\.(edn)?$" (.getName
  ;; %)) fname)  )))
)

(defn flatten-nested-routes [filepaths] nil)

(defn create-routes-from-fpaths
  [filepaths]
  (-> filepaths
      ;; (flatten-nested-routes)
  ))

(defn expand-single-app-path
  [app-path]
  (let [file-obj (clojure.java.io/file app-path)]
    (cond (not (.exists file-obj)) nil
          (.isDirectory file-obj) (->> file-obj
                                       (file-seq)
                                       (filter file-obj-app?))
          :else (list file-obj))))

(comment
  (expand-single-app-path "dsajdjkasbds")
  (expand-single-app-path "test/system/hello_world/_hello-world.app.edn")
  (expand-single-app-path "test/system/person/_person.app.edn")
  (expand-single-app-path "test/system/two_apps/test.app"))

(defn expand-app-paths
  [app-paths]
  (->> app-paths
       (map expand-single-app-path)
       (apply concat)))

(comment
  (expand-app-paths '("dsajdjkasbds"
                      "test/system/hello_world/_hello-world.app.edn"
                      "test/system/two_apps/test.app")))

(defn validate-app-config [app-path] true)

(comment
  (-> "dsadas dsa dsa d sa"
      (str/split (re-pattern (java.io.File/separator)))
      (last))
  (get-name-from-filepath "test/system/hello_world/apps/hello-world.app.edn"))