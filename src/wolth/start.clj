(ns wolth.start
  (:gen-class)
  (:require [wolth.utils.cli :as cli]))

;; (defn -main
;;   "The entry point of lein run"
;;   [& args]
;;   (if (seq? args) (cli/run-cli args) (cli/display-commands)))

(defn start-application [config] nil)

(defn -main
  "The entry point of lein run"
  [& args]
  (if (seq? args) (cli/run-cli args) (cli/display-commands)))

(comment
  (-main "run" "-A" "project.clj")
  )