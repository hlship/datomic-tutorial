(ns dgg.schema
  (:require [datomic.api :as d]))

(def schema-tx
  [#:db{:ident       :bgg/id
        :valueType   :db.type/long
        :cardinality :db.cardinality/one
        :unique      :db.unique/identity
        :doc         "Unique game id at Board Game Geek"}
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
        :doc         "The maximum number of players the game supports"}
   #:db{:ident       :game/publisher
        :valueType   :db.type/ref
        :cardinality :db.cardinality/many
        :doc         "A publisher of the game (a game may be published by different publishers)"}
   #:db{:ident       :bgg/pub-id
        :valueType   :db.type/long
        :cardinality :db.cardinality/one
        :unique      :db.unique/identity
        :doc         "Unique publisher id at Board Game Geek"}
   #:db{:ident       :publisher/name
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}])

(defn transact-schema
  [conn]
  @(d/transact conn schema-tx))
