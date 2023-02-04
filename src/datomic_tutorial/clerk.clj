(ns datomic-tutorial.clerk
  (:require
    [datomic-tutorial.conn :as conn]
    [nextjournal.clerk :as clerk]))

(clerk/serve! {:port        7778
               :browse?     false
               :watch-paths ["src"]})
(conn/startup)

(comment
  (clerk/clear-cache!)

  (clerk/halt!)

  ;; This doesn't yet work
  (clerk/build! {:paths    ["src"]
                 :out-path "target/clerk"})
  )
