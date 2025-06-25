;; # Basic Datomic Queries

;; We'll be using the MusicBrainz database covering 1968-1973.

;; ![MBrainz Relationships](https://github.com/Datomic/mbrainz-sample/raw/master/relationships.png)

;; The [full schema](https://github.com/Datomic/mbrainz-sample/wiki/Schema) describes all the attributes and
;; relationships available to query.

(ns datomic-tutorial.notebook.basic-queries
  {:nextjournal.clerk/toc true}
  (:require [datomic-tutorial.conn :refer [connect]]
            [datomic-tutorial.common :refer [report-exception]]
            [datomic.api :as d :refer [q]]
            [nextjournal.clerk :as clerk]))

;; The first step is to get a database from the Datomic connection:

(def db (d/db (connect)))

;; A Database is a value.  We can query this database to our heart's content and get consistent outputs, taking
;; as long as we want, and regardless of what transactions other clients are performing - it's an immutable snapshot
;; of the evolving Datomic database.

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
;; A query consists of a list of clauses; each group of clauses is preceded by a keyword to define which type
;; of clauses follow; the basic query is three :find clauses followed by two :where clauses.

;; :find clauses identify what will be returned on each successful query: here, the result will be
;; the Datomic entity id, the release's global id, and the release's name.

;; Datomic is a database of Datoms, which is quite different from a traditional SQL rows-and-columns database.
;; Instead of thinking of a single record for each entity stored in the database, think in terms of a set of keys-and-values
;; for each entity.  A Datom is one key/value pair for a specific entity, and everything builds up from there.
;; A Datom consists of ordered slots; the first slot is the _entity_,
;; followed by the _attribute_, and then the _value_.

;; Datomic queries operate by matching the values in the Datoms against the query's :where clauses.

;; The entity is the Datomic-provided id, a long.  These entity ids are the true primary keys inside Datomic, though
;; other attributes may be defined as [unique](https://docs.datomic.com/on-prem/schema/schema.html#db-unique),
;; and serve as alternate entity keys.

;; The attribute must correspond to an attribute previously transacted into the
;; Datomic database's schema (itself stored as Datoms in the database).  The MusicBrainz schema defines
;; the :release/name and :release/gid attributes (along with many others) for release entities; this is a common naming
;; convention - the namespace (`release`) identifies a type of entity the attribute is used with.

;; The attribute value is of a type appropriate for the attribute, according the attribute's schema type: a string, a long, and so forth.

;; The variables, `?e`, `?gid`, and `?name` are special; the leading `?` is important.
;; These are _query variables_.  A query variable may be _bound_ to a specific value, but will initially be _unbound_.

;; When an unbound variable is matched against a Datom, it binds the variable to the value at that position (the entity,
;; attribute, or value) in the Datom.

;; When a bound variable is matched against a Datom, its bound value must exactly match the Datom value at
;; that position (the entity, the attribute, or the value); when the query variables fails to match,
;; Datomic will backtrack, to find other combinations of Datoms and query variables that do match.

;; At its core, a Datomic query is looking for _solutions_; each solution has a specific bound value for
;; each query variable, and that value is consistent across any query clauses it appears in.
;; Once a solution is found, Datomic adds a single result to the result set, then it
;; continues looking for further solutions. This approach, where query variables are bound to values across multiple :where clauses,
;; is called _unification_.

;; ## Getting Tabular Results

;; Datomic returns a set, each value in the set is a vector of the values from the :find clauses.  That's not so pleasant
;; to look at in a Clerk notebook; a little helper function can really improve things.

(defn tq
  "Execute a Datomic query and present the result as a Clerk table."
  [query & more]
  (-> (apply q query more)
    vec
    clerk/table))

;; Converting the set into a vector and passing it to `clerk/table` lets Clerk do some smart formatting, as an HTML table;
;; Clerk still limits how many rows are initially presented.

(tq '[:find ?e ?gid ?name
      :where
      [?e :release/name ?name]
      [?e :release/gid ?gid]]
  db)

;; Now we have orderly rows and columns.

;; Datomic doesn't return query results in any particular order, but of course, it's just Clojure data and easy enough
;; to sort in-memory, if we care.

;; ## Map Queries

;; Generally, queries composed by humans are in the list form we've used so far; however the same results can be accomplished
;; using a map form:

(tq '{:find [?e ?gid ?name]
      :where [[?e :release/name ?name]
              [?e :release/gid ?gid]]}
  db)

;; If you are doing something advanced, such as writing code that assembles queries, you might find that the map
;; format is easier to work with.  It should be obvious that the list format can be (and is!) trivially converted to the
;; map form.

;; ## Map Results

;; So far, we've seen that Datomic queries return a set  of vectors; each vector is populated using the :find clauses.
;; In most cases, we'd prefer to have the individual results organized as map; this is accomplished with :keys clauses:


(tq '[:find ?e ?gid ?name
      :keys id global-id release-name
      :where
      [?e :release/name ?name]
      [?e :release/gid ?gid]]
  db)

;; Since Clerk has been passed a set of maps (not a set of vectors), it knows to format the result as a table, and
;; use the map keys as column headers.

;; Alternately, you may use :strs for string keys, or :syms for symbol keys.

;; ## Relationships

;; MusicBrainz isn't just about albums; it covers all kinds of media and all kinds of releases of those media.
;; So an album might be a release on vinyl, or on CD. And a release may not be an album, it might be a live performance
;; on film, or a broadcast on TV.
;; The MusicBrainz schema identifies that a release has a relationship to a medium, and from there to a track.
;;
;; We can start with a particular track name, and find all the releases for that track.

(tq '[:find ?release-name ?year
      :where
      [?track :track/name "Purple Haze"]
      [?m :medium/tracks ?track]
      [?r :release/media ?m]
      [?r :release/name ?release-name]
      [?r :release/year ?year]]
  db)

;; This matches all tracks with the name "Purple Haze"; it establishes a relationship between the track and
;; the medium; `[?m :medium/tracks ?track]` essentially finds every medium `?m` that contains any matched track `?track`.
;; :medium/tracks is an attribute defined in the schema; it's type is `ref` (a reference to another entity within
;; Datomic), and its [cardinality](https://docs.datomic.com/on-prem/schema/schema.html#db-cardinality) is :cardinality/many, meaning that it can contain many entities, not just one.

;; Expanding from there, `[?r :release/media ?m]` finds every release `?r` that contains any matched medium `?m`.

;; Notice that not all query variables have to appear in the :find clause; `?r` and `?m` are used to link tracks to mediums
;; and then to releases, but aren't part of the query results[^order].

;; [^order]: The order in which :query clauses are listed doesn't affect the set of found solutions; what may change is the order
;; of results, and the [execution time](https://docs.datomic.com/on-prem/query/query-executing.html#clause-order).
;; In general, put the most specific :query clauses, those that will match the fewest Datoms, first.

;; The remaining :query clauses capture the release name and year so that they may be returned by the :find clauses.

;; So a track named "Purple Haze" appears in all these releases.  Again, we're seeing _unification_ yield
;; _solutions_.  Unification ensures that when a value for a query variable, such as `?track`, is established by matching
;; a Datom in a query clause, the query variable's value is consistent across all the query clauses.
;; Once all query variables are bound and unified, that's a single solution.

;; Once a solution is found and added to the results, Datomic will keep working, looking for other solutions.  When all possibilities
;; are exhausted, the query execution is complete.

;; Navigating these relationships can get verbose and tedious; shortly well see alternate methods to gather related data across
;; relationships.

;; ## Query Inputs

;; Hardcoding a track name into our query is not ideal; it would be nice to be able to pass a value into the query from outside,
;; much like a query parameter in a SQL query.

;; This is accomplished using an :in clause:

(tq '[:find ?release-name ?year
      :in $ ?track-name
      :where
      [?track :track/name ?track-name]
      [?m :medium/tracks ?track]
      [?r :release/media ?m]
      [?r :release/name ?release-name]
      [?r :release/year ?year]]
  db "Purple Haze")

;; The :in clause identifies where data for the query comes from.  The `db` is, by convention, the first clause, and
;; **must** be given the name `$`.
;; Here, `?track-name` is bound to "Purple Haze" at the start of query execution,
;; and then unifies across the query clauses normally.

;; ## Multi-valued Query Inputs

;; Having to query one track name at a time is limiting, and Datomic lets us query against a list of values.

;; An alternate input syntax is used when multiple values are passed into the query:

(tq '[:find ?release-name ?year ?track-name
      :in $ [?track-name ...]
      :where
      [?track :track/name ?track-name]
      [?m :medium/tracks ?track]
      [?r :release/media ?m]
      [?r :release/name ?release-name]
      [?r :release/year ?year]]
  db ["Purple Haze" "A Pillow of Winds"])

;; The special syntax, `[?track-name ...]`, indicates to Datomic that `?track-name` is a sequence of values to unify against.

;; ## Pull Syntax

;; Matching many individual attributes just to include them in the :find clause can be
;; tedious; further, we often want to get back a populated entity _map_ rather than a vector of _values_.
;; The pull syntax allows us to just match on Datomic entity id, and gather in
;; all needed attributes without writing further :query clauses.

;; Let's build a query that gathers useful information about a release based on its release name.

(q '[:find (pull ?e [:db/id :release/name :release/year])
     :in $ ?release-name
     :where [?e :release/name ?release-name]]
  db "Meddle")

;; The `pull` clause extracts data and yields a map; we still get back a sequence of solutions; each solution is a vector
;; of one value, and the value is the map created by `pull`[^pulls].

;; As you can see, the last argument to `pull` is a vector of what attributes, of the release entity, to include,
;; but this example is just the most basic case; we'll shortly see more complex pulls.

;; [^pulls]: You can provide multiple `pull`s, but each query variable may only appear in a single
;; :find clause, or an ArrayIndexOutOfBoundsException is thrown.

;; There's [a lot more to pull syntax](https://docs.datomic.com/query/query-pull.html#pull-grammar),
;; to explore, so let's first set up a helper function.

(defn tq-by-release-name
  [db release-name pattern]
  (->> (q '[:find (pull ?e pull-pattern)
            :in $ ?release-name pull-pattern
            :where [?e :release/name ?release-name]]
         db release-name pattern)
    ;; Each pull results in a single map inside a vector, so un-nest the map.
    (mapv first)
    clerk/table))

;; Notice that the variable `pull-pattern` in the query is a simple variable, and not a query variable, as it doesn't
;; have a leading `?`.

;; Using the helper function, we can explore releases and `pull` syntax:

(tq-by-release-name db "Meddle" [:db/id :release/name :release/year])

;; ### Relationships

;; That's seems like an awfully large number of releases for a single album.  Maybe they are on different media?

^{::clerk/width :full}
(tq-by-release-name db "Meddle" [:db/id :release/name :release/year :release/media])

;; Datomic has followed the relationship to the media entity (each release may be on many media).
;; In fact, it has recursively pulled the tracks for each media entity.  This is something to watch out for:
;; `pull` may pull more than you really want!

;; That's more data than we desire, so let's be specific about what to extract from the media entity:

^{::clerk/width :full}
(tq-by-release-name db "Meddle" [:db/id
                                 :release/name
                                 :release/year
                                 {:release/media [:medium/trackCount :medium/format]}])

;; There's a lot of flexibility in the pull specification; the use of a map here indicates a relationship to follow,
;; and the value in the map is a recursive pull specification for the nested entities.  Note that each map
;; should have a single key and value.

;; We're getting close to answering our question about why there are so many releases;
;; we can see that there were quite a few releases per year with different formats, but we can't
;; quite see what those formats are.

;; The :medium/format field contains a ref to a Datomic enum type. Enum values are themselves Datomic entities, but also
;; have a unique identity in :db/ident attribute; this is normally a qualified keyword. We're more interested
;; in that ident than in the numeric entity id, so we can select that instead:

^{::clerk/width :full}
(tq-by-release-name db "Meddle" [:db/id
                                 :release/name
                                 :release/year
                                 {:release/media [:medium/trackCount
                                                  {:medium/format [:db/ident]}]}])

;; By traversing the :medium/format relationship, and pulling the :db/ident attribute we can finally see
;; the different media - vinyl and 12 inch vinyl.

;; An interesting side note about Datomic enums is that you can use the :db/ident value interchangeably with
;; the enum's entity id in query clauses.  Any time Datomic sees a keyword when it expects a entity id (normally, a
;; 64 bit long) it resolves the keyword to an entity (and entity id) using the :db/ident attribute[^ids].

;; [^ids]: This has actually been happening all along; the attributes in the second column of each where clause
;; is supposed to be the attribute's id, and Datomic resolves the keword to an id using a :db/ident lookup; when attributes
;; are defined in the schema, they are given a unique :db/ident value.

;; Let's find all the releases where the :medium/format was :medium.format/vinyl12.

(tq '[:find ?release-name ?release-year
      :where
      [?e :release/name ?release-name]
      [?e :release/year ?release-year]
      [?e :release/media ?m]
      [?m :medium/format :medium.format/vinyl12]]
  db)

;; When Datomic sees a keyword in an Datom slot, it expects to locate an enum entity for that keyword, matching the keyword
;; value against the enum's :db/ident attribute.

;; Code typos can be dangerous, as Datomic will throw an exception if it can't find a matching enum entity:

(report-exception
  (tq '[:find ?release-name ?release-year
        :where
        [?e :release/name ?release-name]
        [?e :release/year ?release-year]
        [?e :release/media ?m]
        [?m :medium/format :medium.formt/vinyl12]]
    db))

;; ### Reverse Relationships

;; So far, we've seen navigating relationship forwards; from an entity containing a ref attribute, to an entity
;; that's referenced - whether the cardinality of the attribute was one or many.  Datomic lets us work backwards as well.

;; This query uses the relationship :track/artists to show all the tracks for the named artists.

(tq '[:find ?artist-name ?track-name
      :keys :artist-name :track-name
      :in $ [?artist-name ...]
      :where
      [?a :artist/name ?artist-name]
      [?t :track/artists ?a]
      [?t :track/name ?track-name]]
  db ["Pink Floyd" "The Rolling Stones"])

;; Using pull syntax, we can start with the artist and work backwards to the track names.

(tq '[:find (pull ?a [:artist/name {:track/_artists [:track/name]}])
      :in $ [?artist-name ...]
      :where
      [?a :artist/name ?artist-name]]
  db ["Pink Floyd" "Wings"])

;; The leading `_` in :track/_artists informs Datomic to work the relationship backwards, from the artist to the
;; track.

;; ### Attribute Pull Options

;; There's still much more to pull syntax.  Next up, we can replace a keyword that identifies an attribute with
;; a vector; the first term in the vector is the attribute, and additional key/value pairs in the vector
;; provide configuration details on what to do with that attribute.


;; #### :as option

;; For example, to change the key used in the result map, use the :as option:

(tq-by-release-name db "Atom Heart Mother"
  [:db/id
   [:release/name :as 'Name]
   [:release/year :as 'Year]])

;; > It appears that :db/id can't be renamed using :as?

;; #### :limit option

;; You don't always need _all_ the data, the :limit option cuts down how many results are returned.
;; This query gets just the first 10 track names for each artist:

(tq '[:find (pull ?a [:artist/name {[:track/_artists :limit 10] [:track/name]}])
      :in $ [?artist-name ...]
      :where
      [?a :artist/name ?artist-name]]
  db ["Pink Floyd" "Wings"])

;; When :limit isn't specified, it defaults to 1000.  A :limit of nil is dangerous - it means no limit whatever.

;; You can also combine options:

(tq '[:find (pull ?a [:artist/name {[:track/_artists :limit 5
                                     :as :tracks]
                                    [:track/name]}])
      :in $ [?artist-name ...]
      :where
      [?a :artist/name ?artist-name]]
  db ["Pink Floyd" "Wings"])


;; #### :default option

;; Attributes for entities are usually optional.  In a SQL database, missing column values
;; will be a null, or a column-specific default (usually applied when a row is inserted).
;; In Datomic, the Datom is simply missing and can't be matched.

;; Paul McCartney is still active, there is no :artist/endYear attribute for Paul, so a query
;; that selects :artist/endYear will simply not have a key in the selected map:

(tq-by-release-name db "McCartney"
  [:release/name
   :release/year
   {:release/artists [:artist/name :artist/endYear]}])

;; In this case, a :default option can be used on the :artist/endYear attribute, to provide a value
;; when no such attribute exists:

(tq-by-release-name db "McCartney"
  [:release/name
   :release/year
   {:release/artists [:artist/name
                      [:artist/endYear :default "Still Active"]]}])

;; Using nil for the default should not be used, as the result is no different than for a missing attribute; nil values are skipped.

;; ### Wildcards

;; Datomic queries support wildcards, which are primarily useful when exploring data
;; at the REPL:

(tq-by-release-name db "Led Zeppelin" '[*])

;; The `*` pattern matches all _attributes_ for each entity.
;; In most cases, entity refs are not expanded, and appear as a map with a lone :db/id key.
;; :release/media is a special case, it is defined as a [component entity](https://docs.datomic.com/schema/schema-reference.html#db-iscomponent),
;; so it is expanded in all its detail.

;; The `*` wildcard can also be used when navigating into a relationship, where it selects
;; all attributes of the target entity of the relationship.

(tq-by-release-name db "Led Zeppelin" '[:db/id
                                        :release/name
                                        :release/year
                                        {:release/media [*]}])

;; ## Collection Queries

;; Normally, a query returns a set of query results, and each result is a vector of values defined by :find clauses.
;; For the occasional query where there's just one :find clause, you can indicate that you just want the values by
;; using the `[?variable ...]` syntax:

(q '[:find [?track-name ...]
     :in $ [?artist-name ...]
     :where
     [?a :artist/name ?artist-name]
     [?t :track/artists ?a]
     [?t :track/name ?track-name]]
  db ["Pink Floyd" "Led Zeppelin"])

;; Notice that the result here is just a vector of track names, not a vector _of vectors_ of a track name.
;; All our previous queries returned a set of results and each result was itself either a vector of values, or a map
;; derived from the matched entity, but here it is just a single _attribute_ from the entity.

;; ## Single Tuple Queries

;; Similar to a collection query, you may want to reduce the results to a single tuple (vector of values);
;; Datomic recognizes this and returns a single vector of the values.

(q '[:find [?year ?month ?day]
     :in $ ?name
     :where
     [?artist :artist/name ?name]
     [?artist :artist/startDay ?day]
     [?artist :artist/startMonth ?month]
     [?artist :artist/startYear ?year]]
  db "Jimi Hendrix")

;; Even if the query _would_ return multiple results, query execution ceases after the first result:

(q '[:find [?year ?month ?day]
     :in $
     :where
     [?artist :artist/startDay ?day]
     [?artist :artist/startMonth ?month]
     [?artist :artist/startYear ?year]]
  db)

;; This query matches _all_ artists, and returns the start date for _some_ artist.

;; ## Single Value Queries

;; And, in the most extreme case, you may expect your query to return a single value and stop.


(q '[:find ?year .
     :in $ ?name
     :where [?artist :artist/name ?name]
     [?artist :artist/startYear ?year]]
  db "Jimi Hendrix")

;; The `.` after `?year` essentially says "stop after one value".

;; ## Aggregate Functions

;; Datomic is not limited to just returning the values directly as stored in Datoms;
;; it has a number of built-in [aggregate](https://docs.datomic.com/query/query-data-reference.html#aggregates)
;; functions that can be used to transform the attribute values.

;; A useful aggregate is `count`, which identifies how many bindings of a query variable occurred.

(q '[:find (count ?r) .
      :in $ ?name
      :where [?artist :artist/name ?name]
      [?r :release/artists ?artist]]
   db "Deep Purple")

;; There are 44 release entities for artist "Deep Purple".

;; Other common aggregates can perform basic statistics; for example, to find the longest and shortest tracks for a particular artist,
;; we can use the `min` and `max` aggregates.

(tq '[:find (min ?dur) (max ?dur)
      :keys :min :max
      :in $ ?artist-name
      :where
      [?a :artist/name ?artist-name]
      [?t :track/artists ?a]
      [?t :track/duration ?dur]]
  db "Pink Floyd")

;; So the shortest track is about 40 seconds, and the longest track is about 23 minutes - as much as will fit on one side of
;; a vinyl LP.

;; With a little work, we can break these results out by year:

(tq '[:find ?year (min ?dur) (max ?dur)
      :keys :year :min :max
      :in $ ?artist-name
      :where
      [?a :artist/name ?artist-name]
      [?t :track/artists ?a]
      [?t :track/duration ?dur]
      [?medium :medium/tracks ?t]
      [?release :release/media ?medium]
      [?release :release/year ?year]]
  db "Pink Floyd")

;; Because `?year` is a :find clause, the solution for a given year includes the `?dur` values for just that year.
;; There's a complex interaction between the :find clauses and the :where clauses that define what a solution is, and
;; for aggregates, what set of values are factored into the aggregate.

;; In any case, I'm not seeing a pattern here.  Let's see the median track lengths as well, and then graph it all!

(let [results (q '[:find ?year (min ?dur) (max ?dur) (median ?dur)
                   :keys :year :min :max :median
                   :in $ ?artist-name
                   :where
                   [?a :artist/name ?artist-name]
                   [?t :track/artists ?a]
                   [?t :track/duration ?dur]
                   [?medium :medium/tracks ?t]
                   [?release :release/media ?medium]
                   [?release :release/year ?year]]
                db "Pink Floyd")
      values (for [{:keys [year] :as datum} results
                   k [:min :max :median]]
               {:year year
                :type k
                :minutes (quot (k datum) 60000)})]
  (clerk/vl
    {:data {:values values}
     :width 500
     :title "Pink Floyd Track Lengths"
     :mark :line
     :encoding {:x {:field :year}
                :y {:field :minutes
                    :type :quantitative}
                :color {:field :type}
                :strokeDash {:field :type}}}))

;; Looks like Pink Floyd really liked to fill an album side for a couple of years before going a bit more
;; "radio friendly".


;; ## Timeouts

;; Query execution is synchronous; you must wait for a response.  In time sensitive code, you can set a timeout, in milliseconds,
;; with a :timeout clause:

(report-exception
  (q '[:find ?e ?gid ?name
       :where
       [?e :release/name ?name]
       [?e :release/gid ?gid]
       :timeout 3]                                          ;; Not enough time!
    db))
