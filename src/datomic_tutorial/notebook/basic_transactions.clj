;; # Basic Datomic Transactions

;; Modifying the data stored in the database.

^{:nextjournal.clerk/toc true}

(ns datomic-tutorial.notebook.basic-transactions
  (:require [datomic-tutorial.conn :as conn]
            [datomic-tutorial.common :refer [report-exception]]
            [datomic.api :refer [q transact]]
            [nextjournal.clerk :as clerk]))

;; Let's talk basics.

;; The active verb here is **transact**, which covers any kind of change to the database; usually adding new
;; data, or changing existing data, but sometimes _retracting_ previous data.

;; Ultimately, transacting is about adding new Datoms to the database; even retracting,
;; which "removes" an attribute, is actually about adding a special Datom that indicates
;; the retraction.


;; ## Transacting a new entity

;; Before we can do anything else, we need a connection to the MBrainz database.

(def conn (conn/connect))

;; Transacting data occurs using a _connection_, not a _database_; A database
;; is a read-only snapshot of the Datomic database at some point in time;
;; a connection is a connection to the Datomic data and Datomic transactor.

;; Let's start by adding an entirely new entity to the database; since this notebook should be repeatable,
;; we'll also define a helper that adds a unique random suffix to strings.

(defn r [s] (str s "-" (random-uuid)))

;; So, transacting an entity is simply writing the entity's Datoms into the database, resulting in a brand
;; new database.

(def result-1 @(transact conn
                         [[:db/add "new-artist" :artist/name (r "Datomic Funkadelic")]
                          [:db/add "new-artist" :artist/gid (random-uuid)]]))

;; From any client's perspective, Datomic transactions are executed asynchronously.
;; Unlike queries, which are processed entirely in the client
;; application's process, transactions are sent to the database's Transactor process.
;; The `transact` function returns a _future_, and the `@` de-references the future, blocking until the transaction
;; is executed and new Datoms are persisted to the data store.

;; The transaction data is a series of tuples; in this case, each one is a :db/add, followed by an entity id,
;; an attribute id, and the value for that entity and attribute.

;; What is "new-artist" here?  It's obviously a string, when true entity ids are longs; this identifies it as a tempid,
;; a placeholder value used to link related data in the transaction.  Datomic will generate a unique
;; entity id for this string, then use it everywhere the string appears where a Datomic entity id is expected.
;; This ensures that the same entity id is used for both
;; attributes, and allows the Transactor to be responsible for assigning the entity id.

;; The result is a map with the following keys:
;; - :db-before - the database value _before_ the transaction was transacted
;; - :db-after - the database value _after_ the transaction was transacted
;; - :tempids - a map from tempid to database id
;; - :tx-data - the new Datoms introduced into the database

;; There was only one tempid in our transaction:

(:tempids result-1)

;; More interesting is the :tx-data value:

(:tx-data result-1)

;; This is a more raw form of how a Datom is stored internally; each Datom consists of:
;; - an entity id (long)
;; - an attribute id (long)
;; - the Datom value
;; - the transaction id (long)
;; - the assert/retract flag (true normally, but false for retractions)

;; There are _three_ Datoms, rather than two, because Datomic _reifies_ transactions - it stores a transaction entity
;; along with the other entities and attributes added or modified in the transaction[^reify].  A transaction is just another
;; kind of Datomic entity, one that has an attribute to define the timestamp of the transaction.  Every Datom
;; in the transaction is linked to the transaction entity, even itself.

;; [^reify]: In traditional databases, a transaction is ephemeral - there might be a data structure used to coordinate the locks
;; and such needed to process a transaction, but that is all discarded once the transaction is committed.  In Datomic,
;; transactions leave a specific trace behind.

;; In raw Datoms, attribute ids are stored as numbers, somewhat obscuring their identity.  It is easier for humans to see
;; the keywords that map to those entity ids. Since the Datomic schema is queryable in Datomic itself,
;; it's easy to translate from entity id to keyword, by using the :db/ident attribute:

(let [attribute-ids (->> result-1 :tx-data (map #(.a %)))
      db (:db-after result-1)]
  (clerk/table
    (q '[:find ?attribute-id ?attribute-name
         :keys :id :ident
         :in $ [?attribute-id ...]
         :where [?attribute-id :db/ident ?attribute-name]]
      db attribute-ids)))

;; You have to squint a little (hex notation vs. decimal) but the transaction attribute's ident is :db/txInstant.

;; ## Reified Transaction Attributes

;; Just like any other entity, it's possible to transact Datoms for the transaction entity; the special tempid "datomic.tx" is
;; used for just this purpose:

(defn t [data]
  @(transact conn data))

(def result-2 (t [[:db/add "new-artist" :artist/name (r "Datomic House Band")]
                  [:db/add "new-artist" :artist/gid (random-uuid)]
                  [:db/add "datomic.tx" :db/doc "Added from the tutorial"]]))

;; Knowing that, we can retrieve the :db/doc attribute for the transaction just as we could any other attribute:

(let [tx-id (get-in result-2 [:tempids "datomic.tx"])
      db (:db-after result-2)]
  (q '[:find ?doc .
       :in $ ?tx-id
       :where [?tx-id :db/doc ?doc]]
    db tx-id))

;; ## Transacting Maps

;; It's very useful to understand this lowest level for submitting transaction data as Datoms, but following such an
;; approach can be quite verbose. In many situations the alternate syntax, based on maps, is more readable and
;; straight forward.

(def result-3 (t [{:artist/name (r "Datomic Country Boys")
                   :artist/gid (random-uuid)}]))

;; This entity map is simply a succinct way of specifying the attributes to add; internally it is expanded into
;; :db/add tuples as in the previous examples.

;; We could easily record multiple entities this way; each map is a new entity, and is expanded into as many
;; :db/add tuples as necessary.

;; Internally, the Transactor uses negative numbers as a different kind of tempid:

(:tempids result-3)

;; That isn't very useful, as those negative numbers were supplied by the Transactor, and then overwritten
;; with actual entity ids.

;; Despite the input being maps, the :tx-data is still a list of Datoms:

(:tx-data result-3)

;; ## Transacting Relationships

;; We can use tempids to create refs between entities.

(def result-3 (t [{:db/id "artist"
                   :artist/name (r "Datomic Hip-Hop All Stars")
                   :artist/gid (random-uuid)}
                  {:track/name (r "Pump Up The Parens")
                   :track/artists "artist"}
                  {:track/name (r "I Get Down with Keywords")
                   :track/artists "artist"}]))

;; Again, Datomic sees a string value plugged in as a value that should be a ref, a numeric reference to an entity.
;; It recognizes the string as a tempid and replaces the string with the entity id.

;; Although :track/artists is cardinality many, we can still specify a single value; cardinality many just means that we can
;; keep asserting new values without retracting old values.  In any case, after transacting, we can get the artist
;; and tracks for that artist:

(clerk/table
  (q '[:find ?artist-name ?track-name
       :keys :artist :track
       :in $ ?artist-id
       :where
       [?artist-id :artist/name ?artist-name]
       [?t :track/artists ?artist-id]
       [?t :track/name ?track-name]]
    (:db-after result-3)
    (get-in result-3 [:tempids "artist"])))

;; But we can go further, and use nested entity maps to transact lots of data and relationships very succinctly:

(def datomic-consortium-gid (random-uuid))

(def result-4 (t [{:db/id "artist"
                   :artist/name (r "Mellow Datomic Consortium")
                   :artist/gid datomic-consortium-gid
                   :artist/type :artist.type/group}
                  {:release/name (r "Solstice Album")
                   :release/artists "artist"
                   :release/media [{:medium/format :medium.format/dat
                                    :medium/tracks [{:track/name (r "Interop Meditations")
                                                     :track/artists "artist"}
                                                    {:track/name (r "Immutability State 0")
                                                     :track/artists "artist"}]}]}]))

;; We can see all the relationships in the :tx-data of the result:

(:tx-data result-4)

;; It's worth diving down into what Datomic does with the keyword :medium.format/dat [^dat].
;; It's assigned to attribute :medium/format which is of type ref, a reference to another entity.
;; As with queries, when you attempt to plug a keyword in as the value of a ref attribute,
;; Datomic will perform a query against the :db/ident attribute to locate the entity id.

;; [^dat]: "dat" is Digital Audio Tape, a high-quality medium replaced by DVDs and streaming.

;; In the MusicBrainz schema, when the :medium/format schema attribute was transacted, a long list of :medium.format/...
;; entities were also transacted; these are used as enumerated values.

;; Nothing (but good sense) prevents you from setting :medium/format to some other entity that has nothing to
;; do with media and formats.  This naming convention is verbose, but useful to keep things organized.

;; ## Modifying Data

;; Modifying existing data is not largely different from what we've seen when adding new data; you transact the changes
;; desired as :db/add tuples, or as entity maps.

;; This is a quick helper to let us translate an artist GID to an artist name:

(defn artist-name [db artist-gid]
  (q '[:find ?artist-name .
       :in $ ?gid
       :where
       [?a :artist/gid ?gid]
       [?a :artist/name ?artist-name]]
    db artist-gid))

;; And this is where we left off.

(artist-name (:db-after result-4) datomic-consortium-gid)

;; But, you know, the lineup of the band changes, the sound shifts a bit, and before you know it, the
;; band changes its name.  That looks like adding a new :artist/name to the existing entity:

(def result-5 (t [[:db/add [:artist/gid datomic-consortium-gid] :artist/name (r "Magical Datomic Consortium")]]))

;; Interestingly, we did not need to know the Datomic entity id in order to make the update; we just need to supply
;; a way of uniquely identifying the entity, so that the Datomic transactor can find the entity id for us.
;; :artist/gid stores a unique UUID, so we can easily reference that.

;; Using the :db-after database, we can see the change:

(artist-name (:db-after result-5) datomic-consortium-gid)

;; Because :artist/name is cardinality one, it can only maintain one value for any single entity; this shows up
;; in the transaction data as a retraction of the old value, followed by the assertion of the new value.

(:tx-data result-5)

;; We can also see the new attribute value with a query:

(artist-name (:db-after result-5) datomic-consortium-gid)

;; And of course, the old database still has the old artist name:

(artist-name (:db-before result-5) datomic-consortium-gid)

;; ## Compare and Set Changes

;; In general, changes you make to Datomic simply overlay existing attribute values ... but there are times
;; when you need to be careful about race conditions; the canonical example is adjusting the balance of a bank
;; account.  In Datomic, there's a compare-and-set operation that allows you to make changes to an attribute
;; only if it is in a known state.

;; Instead of :db/add, we'll use :db/cas, and specify both the expected value and the new value:

(def result-6
  (let [current-name (artist-name (:db-after result-5) datomic-consortium-gid)]
    (t [[:db/cas [:artist/gid datomic-consortium-gid] :artist/name current-name (r "Datomic Jazz Ensemble")]])))

;; Since we provided the correct current value for the attribute, the transaction proceeds normally, and we see the
;; new name reflected in the new database:

(artist-name (:db-after result-6) datomic-consortium-gid)

;; What does it look like when a CAS operation fails?  The call to `transact` is successful, but the Transactor will
;; identify a failure, and an exception is thrown when the future is de-refed:

(let [result-future (transact conn [[:db/cas [:artist/gid datomic-consortium-gid] :artist/name "We Lost The Race" (r "Datomic Jazz Ensemble")]])]
  (report-exception @result-future))

;; Also, :db/cas is sensitive that the attribute value must exist beforehand:

(report-exception
  (t [[:db/cas "does-not-exist" :artist/name "The Spanky Longbottoms" "Elon Musk's Illegitimate Love Child"]]))
