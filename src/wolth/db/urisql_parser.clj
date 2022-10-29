(ns wolth.db.urisql-parser
  (:require [clojure.string :as str]
            [wolth.utils.common :refer
             [find-first cons-not-nil seqs-equal? trim-string]]))

(defn throw-uriql-exception
  [info]
  (throw (RuntimeException. (str "UriqlException. " info))))

(defmacro def-named-token
  [name & exprs]
  `(def ~name (re-pattern (format "(?<%s>%s)" '~name (str ~@exprs)))))

(defn alt-regs [& regs] (re-pattern (str/join "|" regs)))

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
(def-named-token fieldToken "\"[a-zA-Z]([a-zA-Z0-9\\-$])*\"")
(def-named-token valueToken
                 "'([a-zA-Z0-9\\-\\[\\]{}:;.,/?<> !@#$%^&*()-_=+]+)'")

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
      (throw-uriql-exception (str "Cannot parse symbol: " token-str)))
    (find-first (fn [el] (.group matcher-obj el)) tok-group)))

(comment
  (match-token-group "filter" tokenizer-groups)
  (match-token-group "'lalala'" tokenizer-groups)
  (match-token-group "110aaa" tokenizer-groups))

(defn test-for-syntax-errors
  [base-str parsed-strs]
  (when (not= (str/join "" parsed-strs) base-str)
    (throw-uriql-exception (str "Detected unknown tokens in uriql string: "
                                base-str)))
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
   "filter(\"name\"=='John')" "'Lorem Ipsum (123) 111-222-333'"
   "filter(\"name\"=='123 __===++--*^&*%$#@!')"
   "'123 __===++--*^&*%$#@![]{},.?<>')"])

(comment
  (tokenize (_test_tokens 0))
  (tokenize (_test_tokens 1))
  (tokenize (_test_tokens 2))
  (tokenize (_test_tokens 3))
  (tokenize (_test_tokens 4)))

(def uriql-parse-struct
  {:symbols '(),
   :fn-symbol nil,
   :filter-exprs '(),
   :sort-exprs [],
   :offset-expr nil,
   :limit-expr nil,
   :bracket-ctr 0})

(def comp-transform-map {"==" :=, "<>" :<>, ">" :>, "<" :<, "<=" :<=, ">=" :>=})

(def logical-transform-map {"and" :and, "or" :or})

(defn- build-single-filter-operation
  [val-map]
  (when (not= (count val-map) 3)
    (throw-uriql-exception
      (format
        "Have not provided all nessessary fields to build a comparison expression. Missing fields: %s. Tried to evaluate operation: %s"
        (vec (remove (partial get val-map)
               ["comparisonToken" "fieldToken" "valueToken"]))
        val-map)))
  [(comp-transform-map (val-map "comparisonToken"))
   (keyword (trim-string (val-map "fieldToken")))
   (trim-string (val-map "valueToken"))])

(comment
  (build-single-filter-operation
    {"comparisonToken" "==", "fieldToken" "\"aa\"", "valueToken" "'bb"})
  (build-single-filter-operation {"fieldToken" "\"aa\"", "valueToken" "'bb"}))

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
                      (throw-uriql-exception (str "Unknown operation: "
                                                  operation-str)))]
    (if (= operation (first r-val) (first l-val))
      [operation (vec (rest r-val)) (vec (rest l-val))]
      [operation r-val l-val])))

(defn- evaluate-filter-stack
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
  (when (neg? (acc :bracket-ctr))
    (throw-uriql-exception "Detected not closed bracket"))
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
      (throw-uriql-exception "Unmatched bracket in filter statement"))
    (when (not= (count (reduced-exprs :stack)) 1)
      (throw-uriql-exception
        (format
          "Not all filter statements could be evaluated: %s. Try to use brackets to separate filter expressions."
          (vec (rest (reduced-exprs :stack))))))
    (when (some? (reduced-exprs :logical-operation))
      (throw-uriql-exception (str "Redundant logical operand: "
                                  (reduced-exprs :logical-operation))))
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
                       {:type "startBracket", :value "("})))
(def sort-stack-types (list "endBracket" "fieldToken" "startBracket"))

(defn reduce-sort
  [stack direction]
  (when (not= (count stack) 3)
    (throw-uriql-exception
      (format
        "Incorrect number of arguments to a sort function. Got %s, expected 1"
        (- (count stack) 2))))
  (when-not (seqs-equal? (map :type stack) sort-stack-types)
    (throw-uriql-exception
      "Provided wrong type of arguments to a sort function. Expecting one fieldToken (the one surrounded with double brackets)"))
  [(-> stack
       (second)
       (:value)
       (trim-string)
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
      (throw-uriql-exception "Urisql error: Provided unknown function name"))))

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
  (letfn
    [(append-token [struct tok]
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
           (reduce-stack-if-needed item)))
     (parsed-correctly? [parsed-struct]
       (when (not-empty (parsed-struct :symbols))
         (throw-uriql-exception (str "Some symbols could not be evaluated: "
                                     (parsed-struct :symbols))))
       parsed-struct)]
    (->> tokens
         (reduce reducer-fn uriql-parse-struct)
         (parsed-correctly?))))

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
                              {:type "symbolToken", :value "=="})))

(defn validate-uriql-commands!
  [tokens available-commands]
  (if (nil? available-commands)
    tokens
    (do (run! (fn [it]
                (when (and (= (it :type) "functionToken")
                           (nil? (available-commands (it :value))))
                  (throw-uriql-exception (str "Forbidded function token used: "
                                              (it :value)))))
              tokens)
        tokens)))

(comment
  (validate-uriql-commands! (list {:type "functionToken", :value "filter"}
                                  {:type "startBracket", :value "("}
                                  {:type "fieldToken", :value "\"name\""})
                            #{"filter"})
  (validate-uriql-commands! (list {:type "functionToken", :value "filter"}
                                  {:type "startBracket", :value "("}
                                  {:type "fieldToken", :value "\"name\""})
                            #{"sorta"}))

(defn build-honeysql-exprs
  [parse-struct]
  (letfn [(merge-where-clauses [filter-exps]
            (if (= (count filter-exps) 1)
              (first filter-exps)
              (apply vector (cons :and filter-exps))))]
    (cond-> parse-struct
      (not-empty (parse-struct :filter-exprs))
        (assoc :where (merge-where-clauses (parse-struct :filter-exprs)))
      (not-empty (parse-struct :sort-exprs)) (assoc :order-by
                                               (parse-struct :sort-exprs))
      (parse-struct :offset-expr) (assoc :offset (parse-struct :offset-expr))
      (parse-struct :limit-expr) (assoc :limit (parse-struct :limit-expr))
      :finally (select-keys [:where :order-by :offset :limit]))))

(defn apply-uriql-syntax-sugar
  [query]
  (cond (re-matches allToken query) ""
        (re-matches compOnlyToken query) (str "filter(" query ")")
        :else query))

(comment
  (apply-uriql-syntax-sugar "filter(\"name\"=='1')")
  (apply-uriql-syntax-sugar "*")
  (apply-uriql-syntax-sugar "\"id\"=='1234'"))

(defn parse-uriql
  [query & [available-commands]]
  (-> query
      (tokenize)
      (unfold-symbol-tokens)
      (validate-uriql-commands! available-commands)
      (create-parse-stucture)
      (build-honeysql-exprs)))

;; Correct queries
(comment
  ;; (parse-uriql "\"id\"=='jo8wqn-2189nd-a7sas2-qeqwb1'")
  (parse-uriql
    "filter(\"name\"=='1'and\"age\">'10')filter(\"surname\"<>'Kowalski')")
  (parse-uriql "filter(\"name\"=='Lorem Ipsum\\( 123 111-222-333')")
  (parse-uriql "filter(\"name\"=='123 __===++--*^&*%$#@!')")
  (parse-uriql "sorta(\"name\")")
  (parse-uriql "sorta(\"name\")" #{"filter" "sorta"})
  (parse-uriql "sorta(\"name\")sortd(\"created-at\")")
  (parse-uriql
    "filter(\"name\"=='1'and\"surname\"<>'Kowalski')sortd(\"name\")filter(\"age\">'10')")
  (parse-uriql "")
  (parse-uriql "filter((\"aa\"=='bb'and\"cc\">'10')or\"dd\"<>'ee')")
  (parse-uriql
    "sortd(\"surname\")filter((\"aa\"=='bb'and\"cc\">'10')or\"dd\"<>'ee')sorta(\"name\")")
  (parse-uriql
    "filter((\"aa\"=='()bb'and(\"cc\">'10'and(\"ff\">'1'or\"gg\"<'10')))or\"dd\"<>'ee')"))

;; Incorrect queries
(comment
  (parse-uriql "lalalalal")
  (parse-uriql "sorta(\"name\")" #{"filter"})
  (parse-uriql "sorta('1')")
  (parse-uriql "lalal()")
  (parse-uriql "sorta(\"name\" \"lalala\")")
  (parse-uriql "sorta(sorta(\"lll\"))")
  (parse-uriql "filter((\"aa\"=='bb'and\"cc\">10)or\"dd\"<>'ee')")
  (parse-uriql
    "filter(\"name\">='10'and\"surname\"<>'Kowalski'and\"aa\"=='bb')")
  (parse-uriql "filter(\"name\">='10'and\"surname\"<>'Kowalski')("))

