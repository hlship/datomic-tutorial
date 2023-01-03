(ns dgg.publishers
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.tools.reader.edn :as edn]
            [datomic.api :as d]))

(defn read-publishers
  "Reads the publishers EDN file and transforms it into transactable data."
  [path]
  (let [raw-data (-> path io/resource slurp edn/read-string)
        publishers (map (fn [publisher id]
                          (assoc publisher ::id id))
                        raw-data
                        (repeatedly #(d/tempid :db.part/user)))
        publisher-data (mapv #(-> %
                                  (dissoc :games)
                                  (set/rename-keys {:id   :bgg/pub-id
                                                    ::id  :db/id
                                                    :name :publisher/name}))
                             publishers)
        game-data (mapcat (fn [{:keys [::id :games]}]
                            (for [game-id games]
                              {:db/id          [:bgg/id game-id]
                               :game/publisher id}))
                          publishers)]
    (into publisher-data game-data)))

(comment
  (import-publishers "publishers.edn")
  (d/tempid :db.part/user)
  )
