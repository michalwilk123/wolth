(ns wolth.db.urisql-parser
  (:require [clojure.string :as str]
            [wolth.utils.common :refer [find-first cons-not-nil]]
            [wolth.server.exceptions :refer [throw-wolth-exception]]))

(defn throw-uriql-exception
  [info]
  (throw (ex-info "UriqlException" {:info (str "Uriql Error: " info)})))

(defmacro def-named-token
  [name & exprs]
  `(def ~name (re-pattern (format "(?<%s>%s)" '~name (str ~@exprs)))))

(defn alt-regs [& regs] (re-pattern (str/join "|" regs)))
(defn multi-regs [reg] (re-pattern (str "(" reg ")+")))

(def-named-token startBracket "\\(")
(def-named-token endBracket "\\)")


;;;;;;;;; URIQL logical operators ;;;;;;;;;;
(def-named-token andToken "and")
(def-named-token orToken "or")


;;;;;;;;; URIQL comparison operators ;;;;;;;;;;
(def-named-token eqToken "==")
(def-named-token neqToken "<>")
(def-named-token gtToken ">")
(def-named-token geToken ">=")
(def-named-token ltToken "<")
(def-named-token leToken "<=")


;;;;;;;;; URIQL commands ;;;;;;;;;;
(def-named-token sortAscToken "sorta")
(def-named-token sortDescToken "sortd")
(def-named-token filterToken "filter")
(def-named-token offsetToken "offset")
(def-named-token limitToken "limit")
(def-named-token
  functionToken
  (alt-regs sortAscToken sortDescToken filterToken offsetToken limitToken))

(def-named-token logicalToken (alt-regs andToken orToken))
(def-named-token comparisonToken
                 (alt-regs eqToken neqToken gtToken geToken ltToken leToken))


;;;;;;;;; URIQL base syntax ;;;;;;;;;;
(def-named-token symbolToken
                 (alt-regs functionToken logicalToken comparisonToken))
(def-named-token fieldToken "\"[a-zA-Z]([a-zA-Z0-9\\- $]|(\\\\\"))*\"")
(def-named-token valueToken "'([a-zA-Z0-9\\- $%#&=+*]+)'")

(def symbol-groups ["functionToken" "comparisonToken" "logicalToken"])

;;;;;;;;; URIQL special operators ;;;;;;;;;;
(def-named-token allToken "\\*")
(def-named-token compOnlyToken fieldToken comparisonToken valueToken)

(def tokenizer-query
  (alt-regs startBracket endBracket symbolToken fieldToken valueToken))

(def tokenizer-groups
  ["startBracket" "endBracket" "symbolToken" "fieldToken" "valueToken"])

(defn match-token-group
  [token-str tok-group]
  (let [matcher-obj (re-matcher tokenizer-query token-str)]
    (when-not (.matches matcher-obj)
      (throw-wolth-exception :500 (str "cannot parse symbol: " token-str)))
    (find-first (fn [el] (.group matcher-obj el)) tok-group)))

(comment
  (match-token-group "filter" tokenizer-groups)
  (match-token-group "'lalala'" tokenizer-groups)
  (match-token-group "110aaa" tokenizer-groups))

(defn test-for-syntax-errors
  [base-str parsed-strs]
  (when (not= (str/join "" parsed-strs) base-str)
    (throw-wolth-exception :400 "Uriql syntax error"))
  parsed-strs)

(defn tokenize
  [query]
  (->> query
       (re-seq tokenizer-query)
       (map first)
       (test-for-syntax-errors query)
       (map (fn [val]
              {:type (match-token-group val tokenizer-groups), :value val}))))

(def _test_tokens
  ["filter(\"name\"=='1'and\"surname\"=='Kowalski')sorta(\"name\")sortd(\"surname\")"
   "filter(\"name\"=='John')"])

(comment
  (tokenize (_test_tokens 0))
  (tokenize (_test_tokens 1)))

(def uriql-parse-struct
  {:symbols '(),
   :fn-symbol nil,
   :filter-exprs '(),
   :sort-exprs [],
   :offset-expr nil,
   :limit-expr nil,
   :bracket-ctr 0})

(defn- trim-string [text] (subs text 1 (dec (.length text))))

(def comp-transform-map {"==" :=, "<>" :<>, ">" :>, "<" :<, "<=" :<=, ">=" :>=})

(def logical-transform-map {"and" :and, "or" :or})

(def sort-transform-map {"and" :and, "or" :or})

(defn- build-single-filter-operation
  [val-map]
  [(comp-transform-map (val-map "comparisonToken"))
   (trim-string (val-map "fieldToken")) (trim-string (val-map "valueToken"))])

(defn- reduce-filter-expr
  [vals]
  (when vals
    (->> vals
         (reduce (fn [acc it] (assoc acc (it :type) (it :value))) {})
         (build-single-filter-operation))))

(comment
  (reduce-filter-expr (list {:type "valueToken", :value "'John'"}
                            {:type "comparisonToken", :value "=="}
                            {:type "fieldToken", :value "\"name\""})))

(defn- eval-logic-filter-expr
  [l-val operation-str r-val]
  (let [operation (or (logical-transform-map operation-str)
                      (throw (RuntimeException. (str "Unknown operation: "
                                                     operation-str))))]
    (if (= operation (first r-val) (first l-val))
      [operation (vec (rest r-val)) (vec (rest l-val))]
      [operation r-val l-val])))

(defn evaluate-filter-stack
  [acc]
  (let [updated-acc (-> acc
                        (update :bracket-ctr dec)
                        (update :stack
                                (partial cons-not-nil
                                         (reduce-filter-expr (acc :tokens))))
                        (dissoc :tokens))
        stack-tokens (take 3 (updated-acc :stack))]
    (if (= (count stack-tokens) 3)
      (-> updated-acc
          (update :stack (partial drop 3))
          (update :stack
                  (partial cons (apply eval-logic-filter-expr stack-tokens))))
      (assoc updated-acc :stack stack-tokens))))

(defn- filter-reducer-fn
  [acc item]
  (when (neg? (acc :bracket-ctr)) (assert false))
  (case (item :type)
    "endBracket" (update acc :bracket-ctr inc)
    "startBracket" (evaluate-filter-stack acc)
    "logicalToken" (-> acc
                       (update :stack
                               (partial cons-not-nil
                                        (reduce-filter-expr (acc :tokens))))
                       (update :stack (partial cons (item :value)))
                       (dissoc :tokens))
    (update acc :tokens (partial cons item))))


(defn- reduce-filter
  [stack]
  (let [reduced-exprs
          (reduce filter-reducer-fn {:bracket-ctr 0, :stack nil} stack)]
    (when (not (zero? (reduced-exprs :bracket-ctr)))
      (throw (RuntimeException.
               "Uriql Error. Unmatched bracket in filter statement")))
    (when (not= (count (reduced-exprs :stack)) 1)
      (throw (RuntimeException.
               (str "Uriql Error. Not all statements were evaluated: "
                    (reduced-exprs :stack)))))
    (when (some? (reduced-exprs :logical-operation))
      (throw (RuntimeException. (str "Uriql Error. Redundant logical operand: "
                                     (reduced-exprs :logical-operation)))))
    (-> reduced-exprs
        (:stack)
        (first))))

(comment
  (reduce-filter (list {:type "endBracket", :value ")"}
                       {:type "valueToken", :value "'John'"}
                       {:type "comparisonToken", :value "=="}
                       {:type "fieldToken", :value "\"name\""}
                       {:type "startBracket", :value "("}))
  (reduce-filter (list {:type "endBracket", :value ")"}
                       {:type "valueToken", :value "'John'"}
                       {:type "comparisonToken", :value "=="}
                       {:type "fieldToken", :value "\"name\""}
                       {:type "logicalToken", :value "and"}
                       {:type "valueToken", :value "'Alice'"}
                       {:type "comparisonToken", :value ">"}
                       {:type "fieldToken", :value "\"secName\""}
                       {:type "startBracket", :value "("}))
  (reduce-filter (list {:type "endBracket", :value ")"}
                       {:type "endBracket", :value ")"}
                       {:type "valueToken", :value "'John'"}
                       {:type "comparisonToken", :value "=="}
                       {:type "fieldToken", :value "\"name\""}
                       {:type "logicalToken", :value "and"}
                       {:type "valueToken", :value "'Alice'"}
                       {:type "comparisonToken", :value ">"}
                       {:type "fieldToken", :value "\"secName\""}
                       {:type "startBracket", :value "("}
                       {:type "logicalToken", :value "and"}
                       {:type "endBracket", :value ")"}
                       {:type "valueToken", :value "'Alice1'"}
                       {:type "comparisonToken", :value ">"}
                       {:type "fieldToken", :value "\"secName1\""}
                       {:type "logicalToken", :value "or"}
                       {:type "valueToken", :value "'John1'"}
                       {:type "comparisonToken", :value "=="}
                       {:type "fieldToken", :value "\"name1\""}
                       {:type "startBracket", :value "("}
                       {:type "startBracket", :value "("})))

(defn reduce-sort
  [stack direction]
  (println stack)
  [(-> stack
       (second)
       (:value)
       (keyword)) direction])

(defn reduce-uriql-stack
  [struct]
  (let [fn-symbol (struct :fn-symbol)
        sym-stack (take-while (fn [it] (not= (it :type) "functionToken"))
                              (struct :symbols))
        trimmed-struct (-> struct
                           (assoc :fn-symbol nil)
                           (assoc :symbols (->> struct
                                                (:symbols)
                                                (drop-while
                                                  (fn [it]
                                                    (not= (it :type)
                                                          "functionToken")))
                                                (rest))))]
    (case fn-symbol
      "filter" (update trimmed-struct
                       :filter-exprs
                       (partial cons (reduce-filter sym-stack)))
      "sorta" (update trimmed-struct
                      :sort-exprs
                      #(conj % (reduce-sort sym-stack :asc)))
      "sortd" (update trimmed-struct
                      :sort-exprs
                      #(conj % (reduce-sort sym-stack :desc)))
      (throw (RuntimeException. (format "Urisql error: Unknown symbol %s"
                                        fn-symbol))))))

(comment
  (reduce-uriql-stack {:symbols (list {:type "endBracket", :value ")"}
                                      {:type "fieldToken", :value "\"name\""}
                                      {:type "startBracket", :value "("}),
                       :fn-symbol "sorta",
                       :filter-exprs '(),
                       :sort-exprs [],
                       :offset-expr nil,
                       :limit-expr nil,
                       :bracket-ctr 0})
  (reduce-uriql-stack {:symbols (list {:type "endBracket", :value ")"}
                                      {:type "valueToken", :value "'John'"}
                                      {:type "comparisonToken", :value "=="}
                                      {:type "fieldToken", :value "\"name\""}
                                      {:type "startBracket", :value "("}),
                       :fn-symbol "filter",
                       :filter-exprs '(),
                       :sort-exprs [],
                       :offset-expr nil,
                       :limit-expr nil,
                       :bracket-ctr 0}))

(defn create-parse-stucture
  [tokens]
  (letfn [(append-token [struct tok]
            (if (= (tok :type) "functionToken")
              (assoc struct :fn-symbol (tok :value))
              (update struct :symbols (partial cons tok))))
          (update-bracket-count [struct tok]
            (case (tok :type)
              "endBracket" (update struct :bracket-ctr dec)
              "startBracket" (update struct :bracket-ctr inc)
              struct))
          (reduce-stack-if-needed [struct tok]
            (if (and (zero? (struct :bracket-ctr)) (= (tok :type) "endBracket"))
              (reduce-uriql-stack struct)
              struct))
          (reducer-fn [struct item]
            (-> struct
                (append-token item)
                (update-bracket-count item)
                (reduce-stack-if-needed item)))]
    (reduce reducer-fn uriql-parse-struct tokens)))

(comment
  (create-parse-stucture (list {:type "functionToken", :value "filter"}
                               {:type "startBracket", :value "("}
                               {:type "fieldToken", :value "\"name\""}
                               {:type "comparisonToken", :value "=="}
                               {:type "valueToken", :value "'John'"}
                               {:type "endBracket", :value ")"})))

(defn unfold-symbol-tokens
  [tokens]
  (map (fn [tok]
         (if (= (tok :type) "symbolToken")
           (assoc tok :type (match-token-group (tok :value) symbol-groups))
           tok))
    tokens))

(comment
  (unfold-symbol-tokens (list {:type "symbolToken", :value "filter"}
                              {:type "startBracket", :value "("}
                              {:type "fieldToken", :value "\"name\""}
                              {:type "symbolToken", :value "=="}
                              {:type "valueToken", :value "'John'"}
                              {:type "endBracket", :value ")"})))

(defn validate-uriql-commands
  [tokens available-commands]
  (if (nil? available-commands) tokens (do (run! true? tokens) tokens)))

(defn parse-uriql
  [query & [available-commands]]
  (-> query
      (tokenize)
      (validate-uriql-commands available-commands)
      (unfold-symbol-tokens)
      (create-parse-stucture)))

(comment
  (parse-uriql "filter(\"name\"=='1')")
  (parse-uriql
    "filter(\"name\"=='1'and\"surname\"<>'Kowalski')sortd(\"name\")"))

(defn filter-query->uriql [query] (str "filter(" query ")"))

(defn apply-proper-uriql-syntax
  [query]
  (cond (re-matches allToken query) (parse-uriql "")
        (re-matches compOnlyToken query) (parse-uriql (filter-query->uriql
                                                        query))
        :else (parse-uriql query)))

(comment
  (apply-proper-uriql-syntax "filter(\"name\"=='1')")
  (apply-proper-uriql-syntax "*")
  (apply-proper-uriql-syntax "\"id\"=='1234'"))
