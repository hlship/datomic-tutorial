;; # Schema Tuples

;; Storing multiple values in a single attribute.

^{:nextjournal.clerk/toc true}

(ns datomic-tutorial.notebook.tuples
  (:require [datomic-tutorial.conn :as conn]
            [datomic.api :as d]))

;; For these examples, we always start with a fresh and empty
;; Datomic database;

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

;; We're derefing the transaction result, to block until the writes have completed, then using the :db-after key
;; in the result, which is the database value _after_ the new data was transacted.

;; Now, both versions of the `clojure` artifact, as well as the `core.async` artifact, are available to be queried:
(d/q
  '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  db-1 "org.clojure")

;; **NOTE:** You may see additional copies of these, because of how Clerk operates -
;; when refreshing the page, especially after any change,
;; it may have re-executed the `transact` code more than once.

;; ## Preventing Duplication with Tuples

;; We have a problem; _every_ time we transact a new artifact, an entirely new entity is created:

(def db-2 (-> (d/transact conn
                          [{:artifact/group   "org.clojure"
                            :artifact/name    "clojure"
                            :artifact/version "1.11.0"}])
              deref
              :db-after))

;; The `transact` was successful, and quietly created a new entity:

(d/q
  '[:find (pull ?id [*])
    :in $ ?group ?name
    :where [?id :artifact/group ?group]
    [?id :artifact/name ?name]]
  db-2 "org.clojure" "clojure")

;; Datomic always operates by _upserting_; that is, it first attempts to find an existing entity to modify, before creating
;; an entirely new entity.  If you specify a :db/id, Datomic will use that to identify the entity.
;; Alternately, you may specify an attribute that provides
;; [unique identity](https://docs.datomic.com/pro/schema/identity.html#unique-identities); such an attribute
;; uniquely identifies the entity.

;; However, for our artifact entity, we don't _have_ a single unique attribute ... it's the _combination_
;; of group, name, and version that is unique.

;; One approach would be to build a string combining group, name, and version, say `"org.clojure:clojure:1.11.0"`
;; and define a corresponding identity attribute for it.  However, this becomes the responsibility of
;; the client to build the string consistently, and use it to identify existing entities.

;; Instead, we can define a _tuple_ attribute that combines these attribute values.

@(d/transact conn
             [{:db/ident       :artifact/identifier
               :db/cardinality :db.cardinality/one
               :db/unique      :db.unique/identity
               :db/valueType   :db.type/tuple
               :db/tupleAttrs  [:artifact/group :artifact/name :artifact/version]}])

;; This new attribute is assigned when a new entity is created.  Let's create another artifact:

@(d/transact conn
             [{:artifact/group "org.clj-commons"
               :artifact/name "pretty"
               :artifact/version "2.2.1"}])

;;

(d/q
  '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  (d/db conn) "org.clj-commons")

;; You'll see that the new entity has an :artifact/identifier attribute whose value
;; is a vector of the three attributes.

;; If we transact a change with the same group, name, and version, Datomic will find
;; the existing entity.

@(d/transact conn
             [{:artifact/group "org.clj-commons"
               :artifact/name "pretty"
               :artifact/version "2.2.1"
               :db/doc "Updated"}])


@(d/transact conn
             [{:artifact/identifier ["org.clj-commons" "pretty" "2.2.0"]
               :db/doc              "Upserted"}])

(d/q
  '[:find (pull ?id [*])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  (d/db conn) "org.clj-commons")
