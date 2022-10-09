

; source: https://gist.github.com/Sdaas/288f4679204d0f5cbcf6
(defn isPrime?
  "returns true if the input number is a prime number, false otherwise"
  [n]
  (let [divisors (range 2 (inc (int (Math/sqrt n))))
        remainders (map #(mod n %) divisors)]
    (not-any? #(= % 0) remainders)))

;The problem with the previous code is that it has to be read "backwards"
;i.e., from right-to-left. To improve the readability, we rewrite this
;using the "thread-last" macro
(defn nPrimes2
  "compute list of first N primes and optionally apply a fn to each"
  ([n]
   (assert (< n 1000))
   (->> (iterate inc 2)
        (filter isPrime?)
        (take n)))
  ([n f]
   (->> (nPrimes2 n)
        (map f))))

; compute the first 50 prime numbers
(comment
  (nPrimes2 200))
