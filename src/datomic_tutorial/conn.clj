(ns datomic-tutorial.conn
  (:require [datomic.api :as d]))

;; Need to have separate Datomic transactor running:
(def db-uri "datomic:dev://localhost:4334/")

(defonce *connections (atom {}))

(defn connect
  "Connect to a database; default is the mbrainz database."
  ([]
   (connect "mbrainz-1968-1973"))
  ([db-name]
   (let [uri (str db-uri db-name)]
     (if-let [conn (get @*connections uri)]
       conn
       (let [new-conn (d/connect uri)]
         (swap! *connections assoc uri new-conn)
         new-conn)))))

(defn fresh-connection
  "Connect to a fresh, empty database."
  []
  (connect (str "db-" (random-uuid))))
