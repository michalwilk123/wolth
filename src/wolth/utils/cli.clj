(ns wolth.utils.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str])
  (:import (java.net InetAddress))
  (:gen-class))

(defn validate-args [args] args)

(def cli-run-options
  [["-P" "--port PORT" "Port number" :default 8000 :parse-fn
    #(Integer/parseInt %) :validate
    [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--hostname HOST" "Remote host" :default
    (InetAddress/getByName "localhost") :default-desc "localhost" :parse-fn
    #(InetAddress/getByName %)]
   ["-D" "--detach" "Detach from controlling process"]
   ["-v" "--verbosity"
    "Verbosity level; may be specified multiple times to increase value"
    :default "WARNING"] ["-A" "--applications" :required :parse-fn]
   ["-f" "--file NAME" "File names to read" :multi true ; use :update-fn to
                                                        ; combine multiple
                                                        ; instance of -f/--file
    :default []
    ;; with :multi true, the :update-fn is passed both the existing parsed
    ;; value(s) and the new parsed value from each option
    :update-fn conj]
   ;; A boolean option that can explicitly be set to false
   ["-d" "--[no-]daemon" "Daemonize the process" :default true]
   ["-h" "--help"]])

;; (defn run-cli
;;   [& args]
;;   (let [{:keys [action options exit-message ok?]} (validate-args args)]))

(defn run-cli [& args] (println "SIEMANO"))

;;dsadsadsa
(defn display-commands [] (println "dsdsa"))