(ns datomic-tutorial.conn
  (:require [datomic.api :as d]))

;; Need to have separate Datomic transactor running:
(def db-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")

(defonce conn nil)

(defn startup
  []
  (if-not conn
    (do
      (alter-var-root #'conn (fn [_]
                               (d/connect db-uri)))
      :started)
    :already-started))

(defn shutdown
  []
  (if conn
    (do
      (d/release conn)
      (alter-var-root #'conn (constantly nil))
      :stopped)
    :not-started))

(comment
  (startup)
  conn
  (shutdown)

  )
