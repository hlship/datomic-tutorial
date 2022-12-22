(ns dgg.import-csv
  "Imports a collection from a CSV downloaded from boardgamegeek.com"
  (:require
    [clojure.set :as set]
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]))

(defn- read-csv-with-header
  [reader]
  (let [[header & data] (csv/read-csv reader)
        ks (map keyword header)]
    (mapv zipmap (repeat ks) data)))

(defn- xform-csv
  [raw]
  (-> raw
      (select-keys [:objectname
                    :minplayers
                    :maxplayers
                    :objectid])
      (set/rename-keys {:objectname :game/title
                        :minplayers :game/min-players
                        :maxplayers :game/max-players
                        :objectid   :bgg/id})
      (update :bgg/id parse-long)
      (update :game/min-players parse-long)
      (update :game/max-players parse-long)))

(defn read-collection
  "Read a CSV file of a user collection, downloaded from BGG."
  [path]
  (with-open [reader (-> path io/resource io/reader)]
    (->> (read-csv-with-header reader)
         (map xform-csv))))

(comment
  (read-collection "hlship-bgg-collection.csv")

  )
