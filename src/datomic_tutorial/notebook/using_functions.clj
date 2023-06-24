;; # Using Transactor Functions

(ns datomic-tutorial.notebook.using-functions
  (:require [datomic-tutorial.conn :refer [conn]]
            [datomic-tutorial.common :refer [report-exception]]
            [datomic.api :as d :refer [q transact]]
            [nextjournal.clerk :as clerk]))

;; Sometimes, the simple tools for building a transaction are not sufficient; it's necessary to perform some work
;; that is, execute some queries, to determine exactly what data to transact.

;; A common case is when you insert data with the same pattern frequently.  A transactor function can act as a constructor
;; for such cases.

;; Here's an overly simple case where we just want to create a new artist.

(def new-artist (d/function {:lang   :clojure
                             :params '[db artist-name]
                             :code   '[{:artist/name (str artist-name "-" (random-uuid))
                                        :artist/gid  (random-uuid)}]}))

;; This defines the implementation language[^java], the list of function's parameter names (up to 10 parameters
;; are allowed), and the body of the function, which returns transactable data.

;; The code must be quoted, because any references to the parameters (`db` or `artist-name`) are not defined
;; in _this_ context.  Later, this code-as-data will be transferred to the Transactor, which will combine the language,
;; the list of parameter names, and the code into a normal Clojure function that can be executed.
;; Inside that generated function, the parameter names will have context.

;; [^java]: Java is also a supported language.

;; This first parameter is _always_ the current database value; this allows the function
;; to execute whatever queries it needs to.

;; So far, this function only exists as data inside the client; the next
;; step is to store the function _inside_ the database, where the Transactor can access it when needed.

@(transact conn
           [{:db/ident :artist/new
             :db/fn new-artist}])

;; This creates a new entity with the :db/fn containing the function, and it can be referenced
;; as :artist/new because the entity has :artist/new as it's :db/ident attribute.

;; With that in place, we can create new artists more easily, by invoking the function:

(-> (transact conn [[:artist/new "Functional Rockers"]])
    deref
    :tx-data)

;; Inside the returned transaction data, it is possible to pick out the name generated for the artist,
;; and the gid of the artist.

;; It's worth noting that the executed code, whether Clojure or Java, is compiled into bytecode
;; when first loaded - it is not interpreted.

;; TODO: Example with :requires or :imports, maybe use clojure.string namespace.

;; ----

(defn- private-function
  [db]
  [])

(d/function {:lang :clojure
             :params '[db]
             :code '(private-function db)})

;; TODO: This should fail here, or when transacted,or when executed.
