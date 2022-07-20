(ns wolth.generator
  (:require [clojure.string :as str]))

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
      (flatten-nested-routes)
      ()))

(comment
  (-> "dsadas dsa dsa d sa"
      (str/split (re-pattern (java.io.File/separator)))
      (last))
  (get-name-from-filepath "test/system/hello_world/apps/hello-world.app.edn"))