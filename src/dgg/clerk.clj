(ns dgg.clerk
  (:require [nextjournal.clerk :as clerk]))

(clerk/serve! {:port 7778
               :browse? true
               :watch-paths ["src"]})
