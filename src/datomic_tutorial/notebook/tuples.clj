;; Schema Tuples

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
  '[:find (pull ?id [:db/id :artifact/group :artifact/name :artifact/version])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  (d/db conn)
  "org.clojure")

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

;; We're de-refing the result, to ensure that the writes will be visible, then using the :db-after key
;; in the response, which is the database value _after_ the new data was transacted.

;; And those will be visible in our query:

(d/q
  '[:find (pull ?id [:db/id :artifact/group :artifact/name :artifact/version])
    :in $ ?group
    :where [?id :artifact/group ?group]]
  db-1
  "org.clojure")
