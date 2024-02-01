(ns datomic-tutorial.clerk
  (:require
    [babashka.fs :as fs]
    [nextjournal.clerk :as clerk]
    [nextjournal.clerk.home :as home]))

(reset! home/!notebooks (map str (fs/glob "src/datomic_tutorial" "**.{clj,md}")))

(clerk/serve! {:port           7778
               :browse?        false
               :watch-paths    ["src"]})
(comment
  (clerk/clear-cache!)

  (clerk/halt!)

  (fs/delete-tree "target/clerk")

  (clerk/build! {:paths (fs/glob "src" "**/notebook/*.clj")
                 :out-path "target/clerk"})

  )
