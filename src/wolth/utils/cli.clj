(ns wolth.utils.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.net InetAddress))
  (:gen-class))

(defn file-exists? [filename] (.exists (io/file filename)))

(comment
  (file-exists? "project.clj"))

;; TODO: SHOULD CHECK IF APP FILES ARE ENDING WITH .app EXTENSION

(def cli-run-options
  [["-P" "--port" "Port number" :default 8000 :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--hostname HOST" "Remote host" :default
    (InetAddress/getByName "localhost") :default-desc "localhost" :parse-fn
    #(InetAddress/getByName %)]
   ["-D" "--detach" "Detach from controlling process"]
   ["-v" "--verbosity"
    "Verbosity level; may be specified multiple times to increase value"
    :default "WARNING"]
   ["-A" "--applications"
    "List of applications that will be hosted on the platform" :required true
    :multi true :update-fn conj :validate [file-exists?]]
   ["-M" "--modules" "Additional packages to add" :multi true :validate-fn
    file-exists?]
   [nil "--dry-run" "Output generated server code" :default false]
   ;;  A boolean option that can explicitly be set to false
   ["-d" "--[no-]daemon" "Daemonize the process" :default true]
   ["-h" "--help"]])

;; (defn run-cli
;;   [& args]
;;   (let [{:keys [action options exit-message ok?]} (validate-args args)]))


(defn usage
  [options-summary]
  (->>
    ["This is my program. There are many like it, but this one is mine." ""
     "Usage: program-name [options] action" "" "Options:" options-summary ""
     "Actions:" "  start    Start a new server"
     "  stop     Stop an existing server" "  status   Print a server's status"
     "" "Please refer to the manual page for more information."]
    (str/join \newline)))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args
                                                               cli-run-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
        {:exit-message (usage summary), :ok? true}
      errors ; errors => exit with description of errors
        {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments)) (#{"run"} (first arguments)))
        {:action (first arguments), :options options}
      :else ; failed custom validation => exit with usage summary
        {:exit-message (usage summary)})))

;; TODO: have to pass argument to determine if client is running from REPL or
;; prod build
;; (defn exit [status msg] (println msg) (System/exit status))
(defn exit [status msg] (println msg) (println status))

(defn run-cli
  [args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action "run" (println "DZIALAM")))))

;;dsadsadsa
(defn display-commands [] (println "dsdsa"))