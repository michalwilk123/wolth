(ns wolth.utils.cli
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(defn validate-args [args] args)

(def cli-run-options nil)

(def cli-build-options nil)

(defn run-cli
  [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]))


(defn display-commands [] (println "dsdsa"))