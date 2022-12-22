(ns dgg.app
  (:require [dgg.conn :refer [conn] :as conn]
            [dgg.schema :as schema]
            [dgg.import-csv :as csv]
            [datomic.api :as d :refer [q]]))


(defn- tx [tx-data]
  @(d/transact conn tx-data))

(comment
  (d/create-database conn/db-uri)
  (conn/startup)


  (schema/transact-schema conn)


  (tx (csv/read-collection "hlship-bgg-collection.csv"))

  (tx [{:db/id            [:bgg/id 173346]
        :game/summary     "Science? Military? What will you draft to win this head-to-head version of 7 Wonders?"
        :game/description "

In many ways 7 Wonders Duel resembles its parent game 7 Wonders as over three ages players acquire cards that provide resources or advance their military or scientific development in order to develop a civilization and complete wonders.

What's different about 7 Wonders Duel is that, as the title suggests, the game is solely for two players, with the players not drafting cards simultaneously from hands of cards, but from a display of face-down and face-up cards arranged at the start of a round. A player can take a card only if it's not covered by any others, so timing comes into play as well as bonus moves that allow you to take a second card immediately. As in the original game, each card that you acquire can be built, discarded for coins, or used to construct a wonder.

Each player starts with four wonder cards, and the construction of a wonder provides its owner with a special ability. Only seven wonders can be built, though, so one player will end up short.

Players can purchase resources at any time from the bank, or they can gain cards during the game that provide them with resources for future building; as you acquire resources, the cost for those particular resources increases for your opponent, representing your dominance in this area.

A player can win 7 Wonders Duel in one of three ways: each time you acquire a military card, you advance the military marker toward your opponent's capital, giving you a bonus at certain positions; if you reach the opponent's capital, you win the game immediately; similarly, if you acquire any six of seven different scientific symbols, you achieve scientific dominance and win immediately; if none of these situations occurs, then the player with the most points at the end of the game wins.
"}]
      )

  (def db (d/db conn))

  (q '[:find ?bgg-id ?title
       :in $
       :where
       [?id :bgg/id ?bgg-id]
       [?id :game/title ?title]]
     db)

  (q '[:find ?b-id ?summ ?desc
       :in $ ?title
       :where
       [?id :game/title ?title]
       [?id :bgg/id ?b-id]
       [?id :game/summary ?summ]
       [?id :game/description ?desc]]
     db "7 Wonders Duel")

  (conn/shutdown)

  )
