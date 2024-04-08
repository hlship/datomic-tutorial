(ns datomic-tutorial.common
  (:require [datomic.api :as d]
            [nextjournal.clerk :as clerk]))

(defn ex-messages [^Throwable e]
  (lazy-seq
    (let [next (ex-cause e)]
      (cons (ex-message e)
            (when next (ex-messages next))))))

(defn ex-messages-html
  [e]
  (clerk/html
    [:div.text-red-500
     (into [:ul]
           (mapv #(vector :li %) (ex-messages e)))]))

(defmacro report-exception [& forms]
  `(try
     (do ~@forms)
     (throw (RuntimeException. "expected an exception"))
     (catch Throwable e#
       (ex-messages-html e#))))


(defn tq
  "Execute a Datomic query and present the result as a Clerk table."
  [query & more]
  (->> (apply d/q query more)
       ;; Our queries return a seq of matches, and each match is a seq of a single value;
       ;; we can unwrap that to just be a seq of values.
       (mapv first)
       clerk/table))

(defn transact
  "Transact data, and return the Database after the new data is transacted."
  [& args]
  (-> (apply d/transact args)
      deref
      :db-after))
