;; # Datomic Transactions

;; Modifying the data stored in the database.

^{:nextjournal.clerk/toc true}

(ns dgg.notebook.transactions
  (:require [dgg.app :as app]
            [dgg.conn :refer [conn]]
            [datomic.api :as d :refer [q]]
            [dgg.notebook.queries :refer [tq tq-by-title]]
            [nextjournal.clerk :as clerk]))

;; Once again, we'll start with a clean slate.

^::clerk/no-cache
(app/start-fresh)

;; Let's talk basics.

;; The active verb here is **transact**, which covers any kind of change to the database; usually adding new
;; data, or changing existing data, but sometimes _retracting_ previous data.

;; Ultimately, transacting is about adding new Datoms to the database; even retracting,
;; which "removes" an attribute, is actually about adding a special Datom that indicates
;; the retraction.

;; Shortly, we'll see a bit of sugar syntax that allows us to use maps, rather than individual
;; Datoms, but let's start with the most fundamental forms.

;; ## Transacting a new entity

;; I've recently been playing a lot of [Innovation](https://boardgamegeek.com/boardgame/63888/innovation);
;; let's add that to the database.

;; First, a quick check to see if it already exists.

(def db (d/db conn))

(q '[:find [?id]
     :where [?id :game/title "Innovation"]]
   db)

;; Great, let's add it.  We'll just add the Game (we'll cover the relationship to the Publisher later).

(def result
  @(d/transact conn
               '[[:db/add "new-game" :game/title "Innovation"]
                 [:db/add "new-game" :bgg/id 63888]]))

;; What we are transacting is a list of operations to perform; the main operation is :db/add
;; and the other three slots are the same entity id, attribute, and value that we've seen in queries.

;; What is `"new-game"`?  That's a temporary entity id, a placeholder.  As we'll see in a moment,
;; the Transactor will assign a unique value there.

;; Transactions are always asynchronous; the data provided here is bundled up and sent to the
;; Transactor, which performs the work (if possible) and then sends a response once the
;; new data is safely persisted.

;; The Datomic API returns a future here, which can be de-referenced with `@`. In real applications, you
;; are encouraged to use a timeout when de-referencing.

;; ### Looking at the Database

;; Let's peek at the result.

(q '[:find [?id]
     :where [?id :game/title "Innovation"]]
   db)

;; Wait, where is our new game?  The new Datoms _are_ in the database, but they're not in the database _value_ we
;; captured earlier.  That's a big part of what Datomic does, it treats the entire database
;; as a value.  Once we get a database via the connection, no later operations performed by anyone
;; will be visible.  We have a frozen image of the state of the database at that point in time.

(q '[:find [?id]
     :where [?id :game/title "Innovation"]]
   (:db-after result))

;; In order to see the result, we need the version of database _after_ the transaction has
;; been persisted.  That is provided back from the `d/transact` call.

;; Interestingly, just getting a new database value from the connection may or may not work:

(q '[:find [?id]
     :where [?id :game/title "Innovation"]]
   (d/db conn))

;; Sometimes this query works, sometimes it fails.
;; There's a lag between what's safely persisted and what's visible to queries.
;; Eventually, enough transactions accumulate that the Transactor will move
;; the persistent data to the indexes, which is what's visible to queries - typically in a matter for seconds.

;; In general, if you need to read data after a transaction, use the :db-after database value to ensure
;; that the changes you just transacted are visible.

;; ### More on the result

;; The transaction result map has two keys, :db-before and :db-after, that capture the entire database
;; as a value before and after the transaction.
;; More usefully, the result has a :tx-data key with all the new Datoms:

(:tx-data result)

;; This output data is not in the format provided in the call to `transact`, it's raw Datoms.
;; You can see on the second and third lines that there's a new entity for the "Innovation" game, but the first line
;; is a bit of a mystery, as it doesn't appear to relate to the transaction data provided in the call to `transact`.

;; Datomic _reifies_ transactions; inside a transaction operation, a Datomic entity for the transaction
;; is created.  The transaction has a unique entity id, and an attribute with the time of the transaction.
;;
;; In queries, we only looked at the first three Datom columns, but there are actually five columns.
;;
;; The fourth Datom value column is a reference to that transaction.
;; The fifth column is a flag indicating whether the column is a normal add (true), or a retraction (false).

;; So the first Datom is a transaction.  The second column is the attribute id ...
;; but attributes are just another kind Datomic entity, so we see the attribute's entity id (a long)
;; rather than the attribute's identity (a keyword).

;; We can query the list of attribute identities (along with some other entities with a :db/ident):

(->> (q '[:find ?e ?id
          :where [?e :db/ident ?id]]
        db)
     (sort-by first)
     vec)

;; With a little squinting and conversion from hex to decimal, we can pick out :db/txInstant
;; as the attribute in the first Datom, and :game/title and :bgg/id as the attributes in the
;; second and third Datoms.

;; Meanwhile, you'll see that every Datom in `:tx-data` has the same value in the fourth column; the
;; id of the Transaction entity ... including the Transaction entity itself.

;; ### Temporary IDs

;; If Datomic is the one assigning entity ids, as it should, how do we know what id was assigned?
;; This becomes more important in a bit, when we start to transact across relationships.

(:tempids result)

;; In our transaction data, we used `"new-game"` as a temporary id; Datomic notices that this
;; must be a temporary id, because it's a string, and actual entity ids are longs.
;; Datomic allocates a unique entity id for this temporary id, and uses it in the
;; transaction data; Datomic returns a mapping of temporary ids to entity ids as part of the result map.

;; ## Transacting Across Relationships

;; In our application, we have Games that have a relationship to one or more Publishers.
;; Relationships between entities are just attributes whose type is an entity reference, so we can plug in
;; a Publisher's entity id into a Game's :game/publisher attribute:

(def result-2
  @(d/transact conn
               [[:db/add [:bgg/id 63888] :game/publisher "asmadi"]
                [:db/add "asmadi" :bgg/pub-id 5407]
                [:db/add "asmadi" :publisher/name "Asmadi Games"]]))

;; The value `[:bgg/id 63888]` is used to identify a specific game by its unique :bgg/id attribute; Datomic
;; performs a [ref lookup](https://docs.datomic.com/on-prem/schema/identity.html#lookup-refs) to convert it
;; to an entity id.  `"asmadi"` is just a temporary id for the new Publisher being added.

;; This lookup only works when the attribute used for the lookup is indexed and unique and that the value exists
;; prior to the transaction.

;; Again, using the :db-after in the result, we can check that the relationships are present:

(tq-by-title (:db-after result-2)
             "Innovation"
             '[:bgg/id :game/title {:game/publisher [*]}])

;; ## Map Syntax

;; The Datom-based data is the base line for functionality, but can be cumbersome.
;; Datomic supports a second syntax, using maps, that is more concise and often more readable.

;; I just picked up [Terraforming Mars](https://boardgamegeek.com/boardgame/167791/terraforming-mars)
;; and haven't played it yet, but I"m ready to add it to my database.

(def result-3
  @(d/transact conn
               [{:bgg/id         167791
                 :game/title     "Terraforming Mars"
                 :game/summary   "Compete with rival CEOs to make Mars habitable and build your corporate empire."
                 :game/publisher "fryx"}
                {:db/id          "fryx"
                 :bgg/pub-id 18575
                 :publisher/name "FryxGames"}]))

;; This transacts two entities, a Game, and a new Publisher of that game.  Datomic allows a single publisher relationship
;; to be established even though :game/publisher is cardinality many.

;; The results are quite similar to performing the same transaction using Datoms:

(tq-by-title (:db-after result-3)
             "Terraforming Mars"
             '[:bgg/id :game/title {:game/publisher [*]}])

;; The results are still expressed as Datoms expanded from the maps:

(:tx-data result-3)

;; Interestingly, if you inspect the :tempids, you'll see something slightly unexpected:

(:tempids result-3)

;; Our code specified the temporary id `"fryx"`, and we see that mapping for an entity id -
;; but Datomic also allocated a temporary id for the new game as part of expanding
;; the map into Datoms; negative entity ids are also temporary ids, allocated internally by Datomic.


;; ### Nested Relationships

;; We can be even more concise, and avoid the need for providing an explicit temporary id;
;; here's another example of transacting a new Game and set of Publishers together:

(def result-4
  @(d/transact conn
               [{:game/title     "Dune: Imperium"
                 :bgg/id         316554
                 :game/publisher [{:bgg/pub-id 33706
                                   :publisher/name "Dire Wolf"}
                                  {:bgg/pub-id 33626
                                   :publisher/name "Lucky Duck Games"}
                                  {:bgg/pub-id 157
                                   :publisher/name "Asmodee"}]}]))

;; If we check the returned Datoms, we see something interesting:

(:tx-data result-4)

;; There's new Datoms for the game, and for the "Dire Wolf" and "Lucky Duck Games" publishers, but not
;; new Datoms for the "Asmodee" publisher ... though if you check very carefully, you'll see that there
;; is a Datom to establish a :game/publisher relationship from the game to Asmodee.  Datomic was able to
;; use the :bgg/pub-id attribute to resolve the existing Publisher entity.

;; Datomic filters out duplicate Datoms; that is, Datoms that are already present where the only difference would be
;; the new transaction relationship.  This means the exact same syntax can be used for adding entirely new entities,
;; updating existing entities, or a mix of the two.

;; We can verify that all went well with our standard query:


(tq-by-title (:db-after result-4)
             "Dune: Imperium"
             '[:bgg/id :game/title {:game/publisher [*]}])


;; Expanding the :game/publisher value will show all three Publishers are present.

;; There's no real limit to how far these kinds of relationships can be expanded, and the same basic logic stands:
;; convert to Datoms, discard duplicates, and store what's left to the database.
