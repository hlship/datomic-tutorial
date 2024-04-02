;; # Schema Tuples

;; [Tuples](https://docs.datomic.com/pro/schema/schema.html#tuples) are a Datomic feature that allows multiple _values_ to be stored in a single attribute.
;; Tuples have some very specific use cases in Datomic, including certain kinds of optimizations.

;; Primarily, a tuple can be used as a unique key to identify an entity. For large Datomic databases, having all the key values
;; together as a single composite value can be much more efficient; it only involves a single index search - without a tuple
;; key, Datomic would have to search the index for each value composed into the tuple.

;; Beyond efficiency, a tuple key can enforce uniqueness - that is, enforce that the _combination_ of several attributes
;; are unique.

;; Tuples are limited to containing between two and eight values.

(ns datomic-tutorial.notebook.tuples
  {:nextjournal.clerk/toc true}
  (:require [datomic-tutorial.conn :as conn]
            [datomic-tutorial.common :refer [report-exception]]
            [datomic.api :as d]
            [nextjournal.clerk :as clerk]))

;; ## Some Setup

;; Clear the cache before evaluating the whole page; this prevents some runtime errors due to conflicts.
;; If you see an error on this page, just re-run the "open page in Clerk" action.
(clerk/clear-cache!)

;; For these examples, we always start with a fresh, empty, in-memory
;; Datomic database:

(def conn (conn/ephemeral-connection))

;; ## Initial Schema

;; In this example, drawn from some actual work, we are tracking Maven artifacts:
;; Java and Clojure libraries from a repository.  We'll start by defining
;; basic attributes for the group, artifact name, and version.

@(d/transact conn
             [{:db/ident       :artifact/group
               :db/valueType   :db.type/string
               :db/cardinality :db.cardinality/one}
              {:db/ident       :artifact/name
               :db/valueType   :db.type/string
               :db/cardinality :db.cardinality/one}
              {:db/ident       :artifact/version
               :db/valueType   :db.type/string
               :db/cardinality :db.cardinality/one}])

;; We use a leading `@` to de-reference the promise returned by `transact`.
;; The result identifies the state of the database before and after the transaction,
;; as well as exactly what new data was introduced.

;; First, we create a new artifact entity, using the attributes above.

@(d/transact conn
             [{:artifact/group   "org.clojure"
               :artifact/name    "clojure"
               :artifact/version "1.11.1"}])

;; Then we can query for the results:

(d/q
  '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  (d/db conn)
  "org.clojure")

;; That's a bit hard to read, so we'll use a helper function to clean it up:

(defn tq
  "Execute a Datomic query and present the result as a Clerk table."
  [query & more]
  (->> (apply d/q query more)
       ;; Our queries return a seq of matches, and each match is a seq of a single value;
       ;; we can unwrap that to just be a seq of values.
       (mapv first)
       clerk/table))

(tq
  '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  (d/db conn)
  "org.clojure")


;; Refer to [basic queries](basic_queries) for a refresher on Datomic queries.

;; Let's add some additional artifacts:

(def db-1 (-> (d/transact conn
                          [{:artifact/group   "org.clojure"
                            :artifact/name    "clojure"
                            :artifact/version "1.11.0"}
                           {:artifact/group   "org.clojure"
                            :artifact/name    "core.async"
                            :artifact/version "1.6.681"}])
              deref
              :db-after))

;; We're de-referencing the transaction result, to block until the writes have completed, then using the :db-after key
;; in the result, which is the database value _after_ the new data was transacted.

;; Now, both versions of the `clojure` artifact, as well as the `core.async` artifact, are available to be queried:

(tq '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
    db-1 "org.clojure")

;; Since we'll be doing this same transact-then-query pattern repeatedly, let's create another helper function:

(defn transact
  "Transact data, and return the Database after the new data is transacted."
  [& args]
  (-> (apply d/transact args)
      deref
      :db-after))

;; ## Composite Tuples

;; We have a problem; _every_ time we transact a new artifact, an entirely new entity is created:

(def db-2 (transact conn
                    [{:artifact/group   "org.clojure"
                      :artifact/name    "clojure"
                      :artifact/version "1.11.0"
                      :db/doc           "A duplicate"}]))

;; :db/doc is an attribute that allows an entity to be documented; it's normally used with the entities that
;; define the schema, but there's no reason we can't use it here as well.

;; The `transact` was successful, and quietly created a new entity:

(tq '[:find (pull ?id [*])
    :in $ ?group ?name
    :where [?id :artifact/group ?group]
    [?id :artifact/name ?name]]
    db-2 "org.clojure" "clojure")

;; When transacting data, Datomic must decide whether to add new entities, or update existing ones; when you supply
;; the :db/id attribute, Datomic will use that id as the entity to modify.

;; The application-supplied :db/id can be a Datomic id (a long), or (as we'll see in a bit) a value that the Datomic
;; transactor can use to find that Datomic id.

;; When :db/id isn't provided in any form, Datomic will use the provided data to create a new entity, as happened in
;; the example above.

;; However, for our artifact entity, we don't _have_ a _single_ unique attribute to identify an artifact, it's the _combination_
;; of group, name, and version that will uniquely identify an artifact.

;; One approach would be to build a string combining the artifact group, name, and version (e.g. `"org.clojure:clojure:1.11.0"`)
;; and define a corresponding identity attribute for it.  However, this approach makes it the responsibility of
;; the application to build the string consistently, and use it to identify existing entities.

;; Instead, we can define a _composite tuple_ attribute that combines the values of the other attributes.

@(d/transact conn
             [{:db/ident       :artifact/identifier
               :db/cardinality :db.cardinality/one
               :db/unique      :db.unique/identity
               :db/valueType   :db.type/tuple
               :db/tupleAttrs  [:artifact/group :artifact/name :artifact/version]}])

;; The above defined a tuple attribute, and it is a composed tuple because we've supplied what other entity attributes
;; are to be composed.

;; This new attribute is assigned when a new entity is created.  Let's create another artifact:

(def result-3 @(d/transact conn
                    [{:artifact/group   "org.clj-commons"
                      :artifact/name    "pretty"
                      :artifact/version "2.2.1"}]))

;; If you look at the returned :tx-data from the transaction, you'll see that Datomic
;; has transacted the individual components of the composite tuple, as well as the
;; tuple itself:

(:tx-data result-3)

;; And that data is also visible in a normal query:

(tq '[:find (pull ?id [*])
      :in $ ?group
    :where [?id :artifact/group ?group]]
    (:db-after result-3) "org.clj-commons")

;; We can't create another artifact with the same group, name, and version:

(report-exception @(d/transact conn
                               [{:artifact/group   "org.clj-commons"
                                 :artifact/name    "pretty"
                                 :artifact/version "2.2.1"
                                 :db/doc           "Already exists"}]))

;; Because we didn't identify the entity to update with a :db/id attribute, Datomic will
;; attempt to create a new entity.  However, this new entity's :attribute/identifier, generated
;; by Datomic from the other attributes, is not unique.

;; If our client knows what's in the identifier tuple, it can use that to identify what to
;; modify; this is a [lookup ref](https://docs.datomic.com/pro/schema/identity.html#lookup-refs), a special
;; :db/id value as a vector of attribute name and attribute value.

(def db-4 (transact conn
                    [{:db/id  [:artifact/identifier ["org.clj-commons" "pretty" "2.2.1"]]
                      :db/doc "Updated via lookup ref"}]))

;; A lookup ref must specify the value for an attribute that is unique; from that unique value, Datomic can
;; identify the corresponding Database id, and use that to modify the existing entity.

(tq '[:find (pull ?id [*])
      :in $ ?group
      :where [?id :artifact/group ?group]]
    db-4 "org.clj-commons")

;; However, [upsert behavior with tuples](https://forum.datomic.com/t/troubles-with-upsert-on-composite-tuples/1355/3) is still
;; something of a work in progress ... there's a inherent conflict between "you never have to compose a composite tuple" and
;; "you must supply the identity attribute" ... this conflict is yet to be resolved by the Datomic team.

;; That is, since you are not expected to _build_ :artifact/identifier yourself, even if your code already knows
;; the group, artifact name, and iversion. Instead, you can query the database for the :artifact/identifier to include in
;; the transaction to identify the existing entity.  However, you could just as easily query for the :db/id and use _that_
;; to trigger an update.

;; ## Other Tuples

;; Datomic manages composite tuples; application code will transact the underlying attributes of the composite tuple,
;; and Datomic will transact the tuple itself.

;; Datomic supports two application-managed tuple types:

;; [Heterogeneous tuples](https://docs.datomic.com/pro/schema/schema.html#heterogeneous-tuples) contain
;; a fixed number of values, each with a specific type.
;; In this case, the  attribute definition includes a :db/tupleTypes attribute to identify what Datomic type
;; can be stored for each slot in the tuple.


; [Homogeneous tuples](https://docs.datomic.com/pro/schema/schema.html#homogeneous-tuples) contain between
;; two and eight values, all the same type.  In this case, the attribute definition includes a
;; :db/tupleType attribute to identify the Datomic type that can be stored by the tuple.

;; ## Nil values in Tuples

;; Tuple values can be nil.  A nil is always sorted lower/earlier than any non-nil value.

;; ## Tuple Functions in Queries


;; Datomic provides query functions that can compose or break apart tuples.
;;
;; Before investigating them, let's fill in some back-history about org.clj-commons/pretty:

(def db-5 (transact conn
                    (map (fn [version] {:artifact/group   "org.clj-commons"
                                        :artifact/name    "pretty"
                                        :artifact/version version})
                         ["2.0" "2.0.1" "2.0.2" "2.1" "2.1.1" "2.2"])))

;; Next we can query for group, name, and version without reading the individual attributes, just the composite tuple key.
;; We make use of the [untuple](https://docs.datomic.com/pro/query/query.html#untuple) function, which can unify
;; a tuple with a vector of other query variables:

(clerk/table
  (d/q '[:find ?id ?group ?name ?version
         :keys :id :group :name :version
         :where
         [?id :artifact/identifier ?ident]
         [(untuple ?ident) [?group ?name ?version]]]
       db-5))

;; This example is only useful in terms of efficiency: only the :artifact/identifier attribute is read for each
;; entity, so there are fewer index queries than if each of the composed attributes were read for each entity.
;; This kind of optimization may be useful in the right kind of very large Datomic database, but certainly not
;; in a toy database such as this example.

;; Also note that only the `org.clj-commons/pretty` artifacts show up in this query, as they were added _after_ the
;; composite tuple key attribute was added.  Prior entities
;; will not be visible until a transaction involving some of the composed attributes occur.

;; In other cases, you might query several values and want to compose them together as a tuple;
;; for this case, there's the  [tuple](https://docs.datomic.com/pro/query/query.html#tuple) function:

(d/q '[:find [?tup ...]
       :where
       [?id :artifact/group ?group]
       [?id :artifact/name ?name]
       [?id :artifact/version ?version]
       [(tuple ?group ?name ?version) ?tup]]
     db-5)

;; This constructs a tuple from individual values and then unifies that tuple with the other query variable in the clause.

;; Notice this time that all the previously transacted artifacts are included in the response, not just the
;; ones created since the :artifact/identifier attribute was added. This query gets the attributes directly,
;; it doesn't use the :artifact/identifier tuple attribute ... it just _coincidentally_ happens to build a tuple in the same structure.
