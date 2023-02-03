(ns datomic-tutorial.clerk
  (:require [nextjournal.clerk :as clerk]))

(clerk/serve! {:port        7778
               :browse?     false
               :watch-paths ["src"]})

(comment
  (clerk/clear-cache!)

  (clerk/halt!)

  ;; This doesn't yet work
  (clerk/build! {:paths    ["src"]
                 :out-path "target/clerk"})
  )
