(ns dgg.schema
  (:require [datomic.api :as d]))

(def schema-tx
  [#:db{:ident       :bgg/id
        :valueType   :db.type/long
        :cardinality :db.cardinality/one
        :unique :db.unique/identity
        :doc         "Unique object id at Board Game Geek"}
   #:db{:ident       :game/title
        :valueType   :db.type/string
        :cardinality :db.cardinality/one
        :doc         "Title of the game"}
   #:db{:ident       :game/summary
        :valueType   :db.type/string
        :cardinality :db.cardinality/one
        :doc         "A one-line summary of the game"}
   #:db{:ident       :game/description
        :valueType   :db.type/string
        :cardinality :db.cardinality/one
        :doc         "A long-form description of the game"}
   #:db{:ident       :game/min-players
        :valueType   :db.type/long
        :cardinality :db.cardinality/one
        :doc         "The minimum number of players the game supports"}
   #:db{:ident       :game/max-players
        :valueType   :db.type/long
        :cardinality :db.cardinality/one
        :doc         "The maximum number of players the game supports"}])

(defn transact-schema
  [conn]
  @(d/transact conn schema-tx))
