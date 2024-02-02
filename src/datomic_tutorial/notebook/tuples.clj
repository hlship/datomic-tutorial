;; # Schema Tuples

;; [Tuples](https://docs.datomic.com/pro/schema/schema.html#tuples) are a Datomic feature that allows multiple _values_ to be stored in a single attribute.
;; Tuples have some very specific use cases in Datomic, including certain kinds of optimizations.

(ns datomic-tutorial.notebook.tuples
  {:nextjournal.clerk/toc true}
  (:require [datomic-tutorial.conn :as conn]
            [datomic-tutorial.common :refer [report-exception]]
            [datomic.api :as d]
            [nextjournal.clerk :as clerk]))

;; For these examples, we always start with a fresh and empty
;; Datomic database:

(def conn (conn/fresh-connection))

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

;; We can then create a new artifact entity, using the attributes above.

@(d/transact conn
             [{:artifact/group   "org.clojure"
               :artifact/name    "clojure"
               :artifact/version "1.11.1"}])

;; We can then query for the results:

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
       (mapv first)
       clerk/table))

(tq
  '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  (d/db conn)
  "org.clojure")


;; Refer to [basic queries](basic_queries) for a refresher on Datomic queries.

;; We can add additional artifacts as well:

(def db-1 (-> (d/transact conn
                          [{:artifact/group   "org.clojure"
                            :artifact/name    "clojure"
                            :artifact/version "1.11.0"}
                           {:artifact/group   "org.clojure"
                            :artifact/name    "core.async"
                            :artifact/version "1.6.681"}])
              deref
              :db-after))

;; We're dereferencing the transaction result, to block until the writes have completed, then using the :db-after key
;; in the result, which is the database value _after_ the new data was transacted.

;; Now, both versions of the `clojure` artifact, as well as the `core.async` artifact, are available to be queried:
(tq '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
    db-1 "org.clojure")

;; **NOTE:** You may see additional copies of these, because of how Clerk operates -
;; when refreshing the page, especially after any change,
;; it may have re-executed the `transact` code more than once.

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

;; :db/doc is an attribute that allows an entity to be documented; it's normally used with entities that
;; define the schema, but there's no reason we can't use it here as well.

;; The `transact` was successful, and quietly created a new entity:

(tq '[:find (pull ?id [*])
    :in $ ?group ?name
    :where [?id :artifact/group ?group]
    [?id :artifact/name ?name]]
    db-2 "org.clojure" "clojure")

;; Datomic transactions alway operate as _upserts_; that is, Datomic first attempts to find an existing entity to modify, before creating
;; an entirely new entity.  If you specify a :db/id attribute, Datomic will use that id as the entity to modify.
;; Alternately, you may specify an attribute that provides
;; [unique identity](https://docs.datomic.com/pro/schema/identity.html#unique-identities); Datomic can use
;; that unique id to find the :db/id of the entity to modify.

;; When there isn't an existing entity, Datomic will use the provided data to create a new entity.

;; However, for our artifact entity, we don't _have_ a single unique attribute ... it's the _combination_
;; of group, name, and version that is unique.

;; One approach would be to build a string combining group, name, and version, say `"org.clojure:clojure:1.11.0"`
;; and define a corresponding identity attribute for it.  However, this approach makes it the responsibility of
;; the client to build the string consistently, and use it to identify existing entities.

;; Instead, we can define a _tuple_ attribute that combines these attribute values.

@(d/transact conn
             [{:db/ident       :artifact/identifier
               :db/cardinality :db.cardinality/one
               :db/unique      :db.unique/identity
               :db/valueType   :db.type/tuple
               :db/tupleAttrs  [:artifact/group :artifact/name :artifact/version]}])

;; This new attribute is assigned when a new entity is created.  Let's create another artifact:

(def db-3 (transact conn
                    [{:artifact/group   "org.clj-commons"
                      :artifact/name    "pretty"
                      :artifact/version "2.2.1"}]))

(tq '[:find [(pull ?id [*]) ...]
      :in $ ?group
    :where [?id :artifact/group ?group]]
    db-3 "org.clj-commons")

;; You'll see that the new entity has an :artifact/identifier attribute whose value
;; is a vector of the three attributes.

;; We can't create another artifact with the same group, name, and version:

(report-exception @(d/transact conn
                               [{:artifact/group   "org.clj-commons"
                                 :artifact/name    "pretty"
                                 :artifact/version "2.2.1"
                                 :db/doc           "Already exists"}]))


;; If our client knows what's in the identifier tuple, it can use that to identify what to
;; modify; this is a [lookup ref](https://docs.datomic.com/pro/schema/identity.html#lookup-refs), a special
;; :db/id value as a vector of attribute name and attribute value.

(def db-4 (transact conn
                    [{:db/id  [:artifact/identifier ["org.clj-commons" "pretty" "2.2.1"]]
                      :db/doc "Updated via lookup ref"}]))

(tq '[:find (pull ?id [*])
      :in $ ?group
      :where [?id :artifact/group ?group]]
    db-4 "org.clj-commons")

;; However, [upsert behavior with tuples](https://forum.datomic.com/t/troubles-with-upsert-on-composite-tuples/1355/3) is still
;; not working ... there's a conflict between "you never have to compose a composite tuple" and
;; "you must supply the identity attribute" ... this conflict is yet to be resolved.
