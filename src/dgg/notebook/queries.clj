;; # Datomic Queries

^{:nextjournal.clerk/toc true}
(ns dgg.notebook.queries
  (:require [dgg.conn :as conn :refer [conn]]
            [dgg.common :refer [report-exception]]
            [datomic.api :as d :refer [q]]
            [nextjournal.clerk :as clerk]))

;; ## Get a database

(conn/startup)

(def db (d/db conn))

;; ## Simple Query

;; Let's start with a simple query; we'll find every release, returning the release's global id and name,
;; and Datomic's id for each match.

(q '[:find ?e ?gid ?name
     :where
     [?e :release/name ?name]
     [?e :release/gid ?gid]]
   db)

;; This returns over eleven thousand results!

;; So let's break down what the above code does.
;; A query consists of a list of terms; the query always needs at least one :find term and at least one :where term.

;; :find identifies what will be returned on each successful query: the Datomic entity id,
;; the release's global id (gid), and the release's name.

;; Datomic is a database of Datoms.
;; A Datom consist of ordered slots; the first slot is the _entity_,
;; followed by the _attribute_, and then the _value_.

;; Datomic queries operate by matching the values in the Datoms against the query clauses.

;; The entity is the Datomic-provided id, a long.  These entity ids are the real primary keys inside Datomic, though
;; other fields may be indexed or marked unique.

;; The attribute must correspond to an attribute previously transacted into the
;; Datomic database's schema (itself stored as Datoms in the database).

;; The value is of a type appropriate for the attribute, following its schema type: a string, a long, and so forth.

;; The variables, `?e`, `?gid`, and `?name` are special; the leading `?` is important.
;; These are _query variables_.  A query variable may be _bound_ to a specific value, or be _unbound_.

;; At its core, Datomic is looking for _solutions_; each solution has a specific bound value for
;; each query variable, and that value is consistent across any query terms it appears in.
;; Once a solution is found, Datomic adds a single result to the result set, then it
;; continues looking for further solutions.

;; When an unbound variable is matched against a Datom, it binds the variable to the value at that position (the entity,
;; attribute, or value) in the Datom.

;; When a bound variable is matched against a Datom, its bound value must exactly match the Datom value at
;; that position; when it doesn't match, Datomic must backtrack; it will unbind variables and seeks new solutions,
;; until all possibilities are exhausted.

;; ## Getting Tabular Results

;; Datomic returns a set, each value in the set is a vector of the values from the :find clause.  That's not so pleasant
;; to look at in this notebook, but a little helper function can really improve things.

(defn tq [query & more]
  (-> (apply q query more)
      vec
      clerk/table))

;; Converting the set into a vector and passing it to `clerk/table` lets Clerk do some special formatting; it still
;; limits who many rows are initially presented.

(tq '[:find ?e ?gid ?name
      :where
      [?e :release/name ?name]
      [?e :release/gid ?gid]]
    db)

;; Now we have orderly rows and columns.

;; Datomic doesn't return query results in any particular order, but of course, it's just Clojure data and easy enough
;; to sort if we care.

;; ## Relationships

;; MusicBrainz isn't just about albums; it covers all kinds of media and all kinds of releases of those media.
;; So an album might be a release on vinyl, or on CD.  The schema identifies that a release has a
;; relationship to a medium, and from there to a track.

(tq '[:find ?release-name ?year
      :where
      [?track :track/name "Purple Haze"]
      [?r :release/media ?m]
      [?m :medium/tracks ?track]
      [?r :release/name ?release-name]
      [?r :release/year ?year]]
    db)

;; So a track named "Purple Haze" appears in all these releases.  Again, we're seeing _unification_ yield
;; _solutions_.  Unification ensures that when a value for a query variable, such as `?track`, is established,
;; it is consistent across all the query terms.  Once all query variables are bound and unified, that's one
;; solution.

;; Once a solution is found, Datomic will keep working, looking for other solutions.  When all possibilities
;; are exhausted, the query execution is complete.

;; Navigating these relationships can get tedious; shortly well see alternate methods.

;; ## Inputs

;; Hardcoding a track name into our query is not ideal; it would be nice to pass a value into the query from outside.

(tq '[:find ?release-name ?year
      :in $ ?track-name
      :where
      [?track :track/name ?track-name]
      [?r :release/media ?m]
      [?m :medium/tracks ?track]
      [?r :release/name ?release-name]
      [?r :release/year ?year]]
    db "Purple Haze")

;; The :in clause identifies where data for the query comes from.  The `db` is always the first clause, and by
;; convention is given the name `$`.  Here, `?track-name` is bound to "Purple Haze" and then unifies across
;; the query terms normally.

;; ## Multi-valued Inputs

;; Having to query one track name at a time is limiting, and Datomic lets us query against a list of values.

(tq '[:find ?release-name ?year ?track-name
      :in $ [?track-name ...]
      :where
      [?track :track/name ?track-name]
      [?r :release/media ?m]
      [?m :medium/tracks ?track]
      [?r :release/name ?release-name]
      [?r :release/year ?year]]
    db ["Purple Haze" "A Pillow of Winds"])

;; The special syntax, `[?track-name ...]`, indicates to Datomic that `?track-name` is a sequence of values to unify against.

;; ## Pull Syntax

;; Matching many individual attributes just to include them in the :find clause can be
;; tedious; further, we often want to get back a populated _map_ rather than a vector of _values_.
;; The pull syntax allows us to just match on Datomic entity id, and gather in
;; whatever attributes you need.

;; Let's build a query that gather's useful information about release based on its release name.

(q '[:find (pull ?e [:db/id :release/name :release/year])
     :in $ ?release-name
     :where [?e :release/name ?release-name]]
   db "Meddle")

;; The `pull` extracts data and yields a map, so we get back sequence of solutions; each solution is a vector
;; of one value, and the value is the map created by `pull`.

;; > You can provide multiple `pull`s, but each query variable may only appear _once_ in the
;; :find clause, or an ArrayIndexOutOfBoundsException is thrown.

;; There's [a lot more to pull syntax](https://docs.datomic.com/on-prem/query/pull.html#pull-pattern-grammar),
;; to explore, so let's first set up some helpers.

(defn tq-by-release-name
  [db title pattern]
  (->> (q '[:find (pull ?e pull-pattern)
            :in $ ?release-name pull-pattern
            :where [?e :release/name ?release-name]]
          db title pattern)
       ;; Each pull results in a single map inside a vector, so un-nest the map.
       (mapv first)
       clerk/table))

(tq-by-release-name db "Meddle" [:db/id :release/name :release/year])

;; Since Clerk has been passed a vector of maps (not a vector of vectors), it knows to use the map keys as column headers.

;; ### Relationships

;; That's seems like an awfully large number of releases for a single album.  Maybe it is on different media?

(tq-by-release-name db "Meddle" [:db/id :release/name :release/year :release/media])

;; Datomic has followed the relationship to the media entity (each release may be on many media).
;; In fact, it has recursively pulled the tracks for each medium.

;; That's more data than we desire, so let's be more specific about what to extra from the media entity:

(tq-by-release-name db "Meddle" [:db/id
                                 :release/name
                                 :release/year
                                 {:release/media [:medium/trackCount :medium/format]}])

;; There's a lot of flexibility in the pull specification; the use of a map here indicates a relationship to follow,
;; and the value in the map is a recursive pull specification for the nested entities.  Note that each map
;; should have a single key and value.

;; We're getting closer; we can see that there were quite a few releases per year with different formats, but we can't
;; quite see what the formats are.

;; The :medium/format field contains a ref to a Datomic enum type. Enum types are still Datomic entities, but also
;; have a database identity in field :db/ident; this is normally a qualified keyword. We're more interested
;; in that ident than in the entity id, so we can resolve that:

(tq-by-release-name db "Meddle" [:db/id
                                 :release/name
                                 :release/year
                                 {:release/media [:medium/trackCount
                                                  {:medium/format [:db/ident]}]}])

;; By traversing the :medium/format relationship, and pulling the :db/ident attribute we can finally see
;; the different media - vinyl and 12 inch vinyl.

;; An interesting side note about Datomic enums is that you can use the :db/ident value interchangeably with
;; the entity id in queries.

;; Let's find all the releases where the :medium/format was :medium.format/vinyl12.


(tq '[:find ?release-name ?release-year
      :where
      [?e :release/name ?release-name]
      [?e :release/year ?release-year]
      [?e :release/media ?m]
      [?m :medium/format :medium.format/vinyl12]]
    db)

;; When Datomic sees a keyword in an attribute slot, it expects to locate a enum with that ident.

;; Typos can be dangerous, as Datomic will throw an exception if it can't find a matching enum entity:

(report-exception
  (tq '[:find ?release-name ?release-year
        :where
        [?e :release/name ?release-name]
        [?e :release/year ?release-year]
        [?e :release/media ?m]
        [?m :medium/format :medium.formt/vinyl12]]
      db))

;; ### Attribute Options

;; One thing we can do with pull expressions is replace an attribute id with a vector that
;; provides more details on what to do with that attribute id.
#_(tq-by-title db "Codenames" [:bgg/id
                               [:game/min-players :as :min-players]
                               [:game/max-players :as :max-players]
                               [:game/summary :default "N/A" :as :summary]])

;; The :as option renames the key used when constructing the entity map.
;; The :default option provides a default value when the attribute doesn't exist; previously
;; we used `get-else` for this behavior.

;; ### Wildcards

;; Datomic queries support wildcards, which are primarily useful when exploring data
;; at the REPL:
#_(tq-by-title db "Tak" '[*])

;; The `*` pattern matches all _attributes_; it understands entity refs, those are represented
;; as a seq of maps, but each map only contains a `:db/id` attribute.


;; The `*` wildcard can also be used when navigating into a relationship, where it selects
;; all attributes of the target entity of the relationship.
#_(tq-by-title db "Tak" '[:game/title {:game/publisher [*]}])



;; ## Missing Attributes

#_(tq '[:find ?game-id ?title ?summary
        :where
        [?id :bgg/id ?game-id]
        [?id :game/title ?title]
        [?id :game/summary ?summary]]
      db)

;; This query only matches one entity, because `start-fresh` only transacts the summary and description for a single
;; game entity. Still, this raises an important point, because it is unlike a rows-and-columns SQL approach.
;; For all the entities for which no :game/summary has been transacted, the :game/summary attribute
;; is not nil; it is missing entirely. There simply aren't Datoms for Datomic to match against.

;; In a rows-and-columns world, each row will have some value for each column, whether it's a null, or a default value.

;; Datomic will hit that third where clause and (except for that one example) be unable to find a matching Datomc;
;; Datomic will then backtrack to the first clause, where it will match a new
;; :bgg/id and try again.  The results are only for queries where it can simultaneously satisfy _all_
;; of the query clauses, and that's just a single entity in this case.



;; ## Defaults For Missing Attributes

;; So far, we've only seen literal values (such as :bgg/id or :game/title) or query variables (such as `?id` or
;;`?title`) in the where clauses, but that isn't the only option.  Datomic supports certain functions as part of
;; a query clause as well.

;; The `get-else` function is used to locate an attribute of a specific entity, supplying a default value
;; if the Datom does not exist.

#_(tq '[:find ?game-id ?title ?summary ?description
        :in $
        :where
        [?id :bgg/id ?game-id]
        [?id :game/title ?title]
        [(get-else $ ?id :game/summary "") ?summary]
        [(get-else $ ?id :game/description "") ?description]]
      db)

;; > You'll may need to click `More` a few times to get the **7 Wonders Duel**, which has a summary and description.
;; > This table layout  isn't ideal.

;; `get-else` is [built in](https://docs.datomic.com/on-prem/query/query.html#built-in-expressions) to Datomic so there isn't a need to require a namespace to access it.
;; However, you'll notice the new :in clause; this is used make the supplied Database, `db`, available inside the
;; query; it's the first argument to `get-else`.  The use of `$` as the name for this variable is idiomatic.

;; > The :in clause is not actually needed here; if you omit the :in clause entirely, the db is _still_ exposed as `$`.


