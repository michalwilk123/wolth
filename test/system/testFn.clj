
(defn witajFn [x] 
{:status 201 :body {:message "hello world" :argument x :date (.toString (new java.util.Date) )}})