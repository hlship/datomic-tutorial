(ns dgg.conn
  (:require [datomic.api :as d]))

(def db-uri "datomic:mem://game-geek")

(defonce conn nil)

(defn startup
  []
  (alter-var-root #'conn (fn [_]
                           (d/connect db-uri)))
  :started)

(defn shutdown
  []
  (when conn
    (d/release conn)
    (alter-var-root #'conn (constantly nil))
    :stopped))

(comment
  (d/create-database db-uri)

  (startup)
  conn
  (shutdown)

  )
