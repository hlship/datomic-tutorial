;; # Datomic Queries

(ns dgg.notebook.queries
  (:require [dgg.app :as app]
            [dgg.conn :refer [conn]]
            [datomic.api :as d :refer [q]]
            [nextjournal.clerk :as clerk])
  (:import (java.util.jar Attributes)))

(app/start-fresh)

;; ## Get a database

;; The database instance is an immutable view of the live database at a point in time, wh
;; Further updates to the persistent database are invisible to this local reference.

(def db (d/db conn))

;; ## Simple Query

;; This finds all ids and all titles and returns a set of all those ids and tuples.

(q '[:find ?b-id ?title
     :where
     [_ :bgg/id ?b-id]
     [_ :game/title ?title]]
   db)

;; Each where clause identifies an _entity_, an _attribute_, and a _value_.  The entity is the
;; Datomic-provided id, a long.  The attribute must correspond to a attribute previously transacted into the
;; Datomic database's schema (itself stored as Datoms in the database).  The value is of a type
;; appropriate for the attribute, following its schema type: a string, a long, and so forth.

;; Datomic queries work by matching the values in the Datoms against the query clauses.

;; The variables, `?b-id` and `?title` are special (the leading `?` is important). Variables are either bound
;; or unbound.  When an unbound variable is matched against a Datom, it binds to the value at that position (the entity,
;; attribute, or value) in the Datom.
;; When a bound variable is matched against a Datom, its bound value must actually match the Datom value at
;; that position; when it doesn't match, Datomic must backtrack to find a prior clause that can still match.

;; The `_` is a placeholder variable that means "doesn't matter", it can match anything.
;; Datomic has satisified the query by finding all :bgg/id attribute values
;; and all :game/title attribute values across all entities, and returned them ...
;; but it's something like an inner join in SQL; every :bgg/id matched against every :game/title, which is not very useful.

;; ## Binding Variables

;; Adding an `?id` variable to both clauses _unifies_ a single value of the entity id across the where clauses.
;; So Datomic searches for all :bgg/id attributes, and for each one found, binds `?id` to the Datomic entity id,
;; and so finds the matching :game/title (for the same Datomic entity).

;; With all query clauses satisfied, Datomic produces an output value, and then continues searching for more
;; matching clauses, backtracking as necessary.

(q '[:find ?b-id ?title
     :where
     [?id :bgg/id ?b-id]
     [?id :game/title ?title]]
     db)

;; This time, each :bgg/id is matched against the corresponding :game/title.

;; Here, after Datomic matches an id and a title, it will search for another title for the same entity;
;; that will fail (:game/title is cardinality one), and Datomic will backtrack to the first clause and
;; match the next :bgg/id.
;;
;; Eventually, it runs out of :bgg/id values and the query is complete.

;; ## Missing Attributes

(clerk/table
  (vec
    (q '[:find ?b-id ?title ?summary
         :where
         [?id :bgg/id ?b-id]
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

;; # Defaults For Missing Attributes

;; So far, we've only seen literal values (such as :bgg/id or :game/title) or query variables (such as `?id` or
;;`?title` in the where clauses, but that isn't the only option.  Datomic supports function calls as part of
;; a query clause as well.

;; The `get-else` function is used to locate an attribute of a specific entity, supplying a default value
;; if the Datom does not exist.

(clerk/table
  (vec
    (q '[:find ?b-id ?title ?summary
                  :in $
                  :where
                  [?id :bgg/id ?b-id]
                  [(get-else $ ?id :game/title "") ?title]
                  [(get-else $ ?id :game/summary "") ?summary]]
                  db)))

;; `get-else` is _built in_ to Datomic so there isn't a need to require a namespace to access it.
;; However, you'll notice the new :in clause; this is used make the supplied Database, `db`, available inside the
;; query; it's the first argument to `get-else`.  The use of `$` for this variable is idiomatic.
