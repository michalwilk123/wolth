(ns wolth.server.exceptions)

(def exceptions-map
  {:400 "Bad request. User has not provided correct data for given endpoint.",
   :401 "User not authorized to read this object data.",
   :403 "User not authorized to perform such operation on this object.",
   :404 "Resource not found.",
   :500 "Unknown Programming error on the server has occured."})


(defn catch-exceptions
  [func]
  (try (func 1 2)
       (catch clojure.lang.ExceptionInfo e (println (str "EEEE" e)))))

(comment
  (catch-exceptions (fn [a b]
                      (throw (ex-info "Catched Exception!" {:type "siema"})))))


(defn throw-wolth-exception
  ([code additional-info]
   (if-let [exc-message (exceptions-map code)]
     (throw (ex-info (or additional-info exc-message) {:type code}))
     (throw-wolth-exception :500
                            (str "Unknown exception code thrown: "
                                 (name code)))))
  ([code] (throw-wolth-exception code nil)))

(comment
  (throw-wolth-exception :401 "Unknown user")
  (throw-wolth-exception :1000))
