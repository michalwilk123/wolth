(ns wolth.utils.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))

(def help-man-path "src/wolth/utils/help.txt")

(defn show-help [] (print "this is a help message"))

(defn file-exists? [filename] (.exists (io/file filename)))

(comment
  (show-help)
  (file-exists? "project.clj"))

;; TODO: SHOULD CHECK IF APP FILES ARE ENDING WITH .app EXTENSION

(def cli-run-options
  [["-A" "--applications"
    "List of applications that will be hosted on the platform" :required true
    :multi true :update-fn conj :validate [file-exists?]]])

(defn usage
  [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        "Usage: program-name [options] action. Options:" options-summary
        "Actions:"]
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
      (= 1 (count arguments)) {:action (first arguments), :options options}
      :else ; failed custom validation => exit with usage summary
        {:exit-message (usage summary)})))

;; TODO: have to pass argument to determine if client is running from REPL or
;; prod build
;; (defn exit [status msg] (println msg) (System/exit status))
(defn exit [status msg] (println msg) (println status))

;; (defn run-cli
;;   [args]
;;   (let [{:keys [action options exit-message ok?]} (validate-args args)]
;;     (if exit-message
;;       (exit (if ok? 0 1) exit-message)
;;       (case action
;;         "run" (println "DZIALAM" options)
;;         "dry-run" (println options)
;;         "help" (println "pomocna wiadomość")))))

(defn display-commands [] (println "dsdsa"))

(defn create-server-config-from-cli-opts [opts] {})