(ns datomic-tutorial.clerk
  (:require
    [datomic-tutorial.conn :as conn]
    [babashka.fs :as fs]
    [nextjournal.clerk :as clerk]))

(clerk/serve! {:port 7778
               :browse? true
               :watch-paths ["src"]})
(conn/startup)

(comment
  (clerk/clear-cache!)

  (clerk/halt!)

  (fs/delete-tree "target/clerk")

  (clerk/build! {:paths (fs/glob "src" "**/notebook/*.clj")
                 :out-path "target/clerk"})

  )
