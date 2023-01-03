;; # Datomic Queries

^{:nextjournal.clerk/toc true}
(ns dgg.notebook.queries
  (:require [dgg.app :as app]
            [dgg.conn :refer [conn]]
            [datomic.api :as d :refer [q]]
            [nextjournal.clerk :as clerk]))

;; Start a fresh copy of an in-memory database.

(app/start-fresh)

;; ## Get a database

;; The database instance is an immutable view of the live database at a point in time.
;; Further updates to the persistent database are invisible to this local reference.

(def db (d/db conn))

;; ## Simple Query

;; This finds all ids and all titles and returns a set of all those ids and tuples.

(q '[:find ?game-id ?title
     :where
     [_ :bgg/id ?game-id]
     [_ :game/title ?title]]
   db)

;; Each where clause identifies an _entity_, an _attribute_, and a _value_.  The entity is the
;; Datomic-provided id, a long.  The attribute must correspond to an attribute previously transacted into the
;; Datomic database's schema (itself stored as Datoms in the database).  The value is of a type
;; appropriate for the attribute, following its schema type: a string, a long, and so forth.

;; Datomic queries work by matching the values in the Datoms against the query clauses.

;; The variables, `?game-id` and `?title` are special (the leading `?` is important). Variables are either bound
;; or unbound.  When an unbound variable is matched against a Datom, it binds to the value at that position (the entity,
;; attribute, or value) in the Datom.
;; When a bound variable is matched against a Datom, its bound value must actually match the Datom value at
;; that position; when it doesn't match, Datomic must backtrack to find a prior clause that can still match.

;; The `_` is a placeholder variable that means "doesn't matter", it can match anything.
;; Datomic has satisfied the query by finding all :bgg/id attribute values
;; and all :game/title attribute values across all entities, and returned them ...
;; but it's something like an outer join in SQL; every :bgg/id matched against every :game/title, which is not very useful.

;; ## Binding Variables

;; Adding an `?id` variable to both clauses _unifies_ a single value of the entity id across _all_ where clauses.
;; So Datomic searches for all :bgg/id attributes, and for each one found, binds `?id` to the Datomic entity id,
;; and so finds the matching :game/title (for the same Datomic entity).

;; With all query clauses satisfied, Datomic produces a result; this is defined by the :find clause.
;; Datomic then backtracks to a point where it can re-bind a variable to the next available value.
;; The query execution completes when all possible values for all query variables have been exhausted.

;; The trick is to link the :bgg/id and :game/title attributes:

(q '[:find ?game-id ?title
     :where
     [?id :bgg/id ?game-id]
     [?id :game/title ?title]]
   db)

;; This time, each :bgg/id is matched against the corresponding :game/title. The specific value for
;; `?id` must be consistent across all where clauses.

;; Here, after Datomic matches an id and a title, it will search for another title for the same entity;
;; that will fail (:game/title is cardinality one), and Datomic will backtrack to the first clause and
;; match the next :bgg/id.
;;
;; Eventually, it runs out of :bgg/id values and the query is complete.

;; ## Missing Attributes

(clerk/table
  (vec
    (q '[:find ?game-id ?title ?summary
         :where
         [?id :bgg/id ?game-id]
         [?id :game/title ?title]
         [?id :game/summary ?summary]]
       db)))

;; This query only matches one entity, because `start-fresh` only transacts the summary and description for a single
;; game entity. Still, this raises an important point, because it is unlike a rows-and-columns SQL approach.
;; For all the entities for which no :game/summary has been transacted, the attribute
;; is not nil, it is missing entirely. There simply aren't Datoms for Datomic to match against.

;; In a rows-and-columns world, each row will have some value for each column, whether it's a null or a default value.

;; Datomic will hit that third where clause and backtrack to the first clause, where it will match a new
;; :bgg/id and try again.  The results are only for queries where it can simultaneously satisfy _all_
;; of the query clauses, and that's just a single entity in this case.

;; ## Defaults For Missing Attributes

;; So far, we've only seen literal values (such as :bgg/id or :game/title) or query variables (such as `?id` or
;;`?title`) in the where clauses, but that isn't the only option.  Datomic supports certain functions as part of
;; a query clause as well.

;; The `get-else` function is used to locate an attribute of a specific entity, supplying a default value
;; if the Datom does not exist.

(clerk/table
  (vec
    (q '[:find ?game-id ?title ?summary ?description
         :in $
         :where
         [?id :bgg/id ?game-id]
         [?id :game/title ?title]
         [(get-else $ ?id :game/summary "") ?summary]
         [(get-else $ ?id :game/description "") ?description]]
       db)))

;; > You'll may need to click `More` a few times to get the **7 Wonders Duel**, which has a summary and description.
;; > This table layout  isn't ideal.

;; `get-else` is [built in](https://docs.datomic.com/on-prem/query/query.html#built-in-expressions) to Datomic so there isn't a need to require a namespace to access it.
;; However, you'll notice the new :in clause; this is used make the supplied Database, `db`, available inside the
;; query; it's the first argument to `get-else`.  The use of `$` for this variable is idiomatic.

;; > The :in clause is not actually needed here; if you omit the :in clause entirely, the db is _still_ exposed as `$`.

;; ## Relationships

(clerk/table
  (vec
    (q '[:find ?game-id ?title ?publisher-name
         :in $ ?title
         :where
         [?id :game/title ?title]
         [?id :bgg/id ?game-id]
         [?id :game/publisher ?pub-id]
         [?pub-id :publisher/name ?publisher-name]]
       db "Tak")))

;; "Tak", like many games, has had a number of publishers over time; our sample data includes two.
;; We've expressed the relationship by finding the :game/publisher attribute to `?pub-id`.  :game/publisher
;; is a :db.type/ref, a reference to another entity in the database.  Then we can use `?pub-id` in the
;; entity id column of the next query clause, and the same binding rules work to ensure that the :publisher/name
;; from the correct entity is selected.

;; The "Tak" entity has multiple Datoms for the :publisher/name attribute, so the query is succesful for
;; both of them.

;; We don't have to say that an entity is a Publisher any more than we previously had to say that a game is
;; Game.  The "type" of an entity is am emergent property: if it has the right subset of attributes, then
;; it is a Game, or a Publisher.  You could do nonsensical things like have an entity with both Game and Publisher
;; attributes, but that's on you, not Datomic.

;; ## Multi-valued Inputs

;; In the prior example, `?title` was bound to a single value.  Datomic can repeatedly bind a variable to a sequence
;; of values:

(clerk/table
  (vec
    (q '[:find ?game-id ?title ?max-players
         :in $ [?title ...]
         :where
         [?id :bgg/id ?game-id]
         [?id :game/max-players ?max-players]
         [?id :game/title ?title]]
       db ["Tak" "Codenames"])))


;; The special syntax `[?title ...]` informs Datomic that this is a multi-valued binding to
;; iterate over, rather than a single value that happens to be a vector.

;; ## Pull Syntax

;; Matching many individual attributes just to include them in the :find clause can be
;; tedious; further, we often want to get back a populated _map_ rather than a vector of _values_.
;; The pull syntax allows you to just match on Datomic entity id, and gather in
;; whatever attributes you need.

;; Let's build a query that gather's useful information about a game based on its title.

(q '[:find (pull ?e [:db/id :bgg/id :game/title :game/summary])
     :in $ ?title
     :where [?e :game/title ?title]]
   db "Tak")

;; Some things to note: The :in clause matches the arguments to `q` to local variables and query variables.

;; The :where clause matches the provided `?title` and binds the `?e` variable, which can then be used in the
;; :find clause.

;; The `pull` function is very tolerant of attributes that don't exist for the entity.  "Tak"
;; doesn't have a :game/summary, so that key is simply omitted from the result map (which is very different
;; from supplying a default value, as with `get-else`).

;; > You can provide multiple `pull`s, but each query variable may only appear _once_ in the
;; :find clause, or an ArrayIndexOutOfBoundsException is thrown.

;; There's [a lot more to pull syntax](https://docs.datomic.com/on-prem/query/pull.html#pull-pattern-grammar),
;; to explore, so let's first set up some helpers.

(defn title-query
  [db title pattern]
  (->> (q '[:find (pull ?e pull-pattern)
            :in $ ?title pull-pattern
            :where [?e :game/title ?title]]
          db title pattern)
       ;; Each pull results in a single map inside a vector, so un-nest the map.
       (mapv first)
       clerk/table))

(title-query db "Codenames" [:bgg/id :game/min-players :game/max-players])

;; This new `title-query` function makes it easy to match an entity by its :game/title, then make use of
;; the provided pull pattern.

;; ### Relationships

;; Pull patterns can be maps; the keys of the map indicate what relationship to traverse,
;; and the value is a sub-pattern to run on the entity traversed to.

(title-query db "Codenames" [:bgg/id :game/title {:game/publisher [:bgg/id :publisher/name]}])

;; The fetched Publisher data is not flattened into two columns, it is a seq of maps as a single value.

;; Tak is more interesting, as it has multiple publishers:

(title-query db "Tak" [:bgg/id :game/title {:game/publisher [:bgg/id :publisher/name ]}])

;; This makes it clear that the third column value is a seq of maps ... and that's likely
;; a better representation for your application than the previous approach at getting the
;; publisher names, that returns multiple results.

;; > TODO: What if the map has multiple keys?

;; ### Attribute Options

;; One thing we can do with pull expressions is replace an attribute id with a vector that
;; provides more details on what to do with that attribute id.

(title-query db "Codenames" [:bgg/id
                             [:game/min-players :as :min-players]
                             [:game/max-players :as :max-players]
                             [:game/summary :default "N/A" :as :summary]])

;; The :as option renames the key used when constructing the entity map.
;; The :default option provides a default value when the attribute doesn't exist; previously
;; we used `get-else` for this behavior.

;; ### Wildcards

(title-query db "Tak" '[*])

;; The `*` path matches all _attributes_; it understands entity refs, those are represented
;; as a seq of maps, but each map only contains a `:db/id` attribute.

(title-query db "Tak" '[:game/title {:game/publisher [*]}])
