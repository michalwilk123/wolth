(ns wolth.server.exceptions
  (:import [java.lang AssertionError Exception])
  (:require [clojure.stacktrace :refer [print-stack-trace]]
            [io.pedestal.log :as log]))

(def exceptions-map
  {:400 "Bad request. User has not provided correct data for given endpoint.",
   :401 "User not authorized to read this object data.",
   :403 "User not authorized to perform such operation on this object.",
   :404 "Resource not found.",
   :405 "Method not allowed",
   :500 "Unknown Programming error on the server has occured."})


(defn throw-wolth-exception
  ([code additional-info]
   (if-let [exc-message (exceptions-map code)]
     (let [payload {:status (Integer/parseInt (name code)),
                    :body (or additional-info exc-message)}]
       (println payload)
       (log/error ::WOLTH-EXCEPTION payload)
       (throw (ex-info "WolthException" payload)))
     (throw-wolth-exception :500
                            (str "Unknown exception code thrown: "
                                 (name code)))))
  ([code] (throw-wolth-exception code nil)))

(comment
  (throw-wolth-exception :401 "Unknown user")
  (throw-wolth-exception :1000))

#_"
Interceptor macro has to objectives:
 * catches common exceptions and if they occur adds additional information 
   about an exception to the context
 * if it founds info about the exception occuring it ignores the excecution of
   the function body and propagates the exception forward
   "
(defmacro def-interceptor-fn
  [name args & body]
  (assert (= (count `~args) 1)
          "Interceptor function should intake only one argument")
  `(def ~name
     (fn ~args
       (let [ctx# (first ~args)
             exception-occured# (ctx# :exception-occured)]
         (if exception-occured#
           ctx#
           (try
             ~@body
             (catch AssertionError e#
               (merge ctx#
                      {:response {:status 400,
                                  :body (str "Got unexpected data. Error:" e#
                                             " Traceback: " (with-out-str
                                                              (print-stack-trace
                                                                e#)))},
                       :exception-occured true}))
             (catch clojure.lang.ExceptionInfo e#
               (merge ctx# {:exception-occured true, :response (ex-data e#)}))
             (catch Exception e#
               (merge ctx#
                      {:exception-occured true,
                       :response {:status 500,
                                  :body (str "Got unexpected error: " e#
                                             " Traceback: " (with-out-str
                                                              (print-stack-trace
                                                                e#)))}}))))))))



(comment
  #_(clojure.walk/macroexpand-all
      '(def-interceptor-fn numer [x] (println "dadsa: " x) (println "QQQQ")))
  (def-interceptor-fn ff [_ctx _x] (println "hello world"))
  (def-interceptor-fn ff [ctx] (assoc ctx :result "udalo sie!"))
  (def-interceptor-fn gg [_ctx] (assert false))
  (def-interceptor-fn hh [_ctx] (/ 10 0))
  (def-interceptor-fn jj [_ctx] (throw-wolth-exception :404 "NIE ZNALEZIONO"))
  (ff {:json {:name "Jake"}})
  (gg {:json {:name "Jake"}})
  (hh {:json {:name "Jake"}})
  (jj {:json {:name "Jake"}})
  (ff {:exception-occured true,
       :response {:status 401, :body "BLAD AUTORYZACJI"}}))

