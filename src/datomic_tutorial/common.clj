(ns datomic-tutorial.common
  (:require [nextjournal.clerk :as clerk]))

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

