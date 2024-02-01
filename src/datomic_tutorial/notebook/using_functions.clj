;; # Using Transactor Functions

(ns datomic-tutorial.notebook.using-functions
  (:require [clojure.string :as str]
            [datomic-tutorial.conn :as conn]
            [datomic-tutorial.common :refer [report-exception]]
            [datomic.api :as d :refer [q transact]]
            [nextjournal.clerk :as clerk]))

;; Sometimes, the simple tools for building a transaction are not entirely sufficient; there are times when it is useful
;; or even unavoidable, for the transactor to perform a query _during the transaction_ and use the information gathered
;; as part of the transaction itself.

;; This is the domain of the _transactor function_, which is code that executes _inside_
;; the transactor, during the execution of a transaction.

;; A common case is when you insert data with the same pattern frequently.  A transactor function can act as a constructor
;; for such cases.

;; Here's an overly simple case where we just want to create a new artist.

(def conn (conn/connect))

(def new-artist (d/function '{:lang   :clojure
                              :params [db artist-name]
                              :code   [{:artist/name (str artist-name "-" (random-uuid))
                                        :artist/gid  (random-uuid)}]}))

;; This defines the implementation language[^java], the list of the function's parameter names (up to 10 parameters
;; are allowed), and the body of the function, which must return transactable data.

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
;; as :artist/new because the entity has :artist/new as its :db/ident attribute.

;; With that in place, we can create new artists more easily, by invoking the function:

(-> (transact conn [[:artist/new "Functional Rockers"]])
    deref
    :tx-data)

;; Inside the returned transaction data, it is possible to pick out the name generated for the artist,
;; and the gid of the artist.

;; It's worth noting that the executed code, whether Clojure or Java, is compiled into bytecode
;; when first loaded - it is not interpreted.

;; ## Requires

;; The above example is quite oversimplified; the most common thing a transactor function will
;; do is to perform its own queries against the database.  A transactor function is passed the
;; database, and can use the `datomic.api` namespace to execute queries.

;; The :requires key of a function is "spliced into" the requires for the function (it's as if
;; the transactor constructs a namespace around the individual function). In any case, to reference
;; functions in a namespace, the namespace must be required.

;; This example performs a query to convert a artist name to a database id. This is a bit silly -- the
;; peer could just as easily do it -- but in some cases, it can make sense for the transactor to perform
;; some specific validations that can't effectively be done in the peer.

;; In any case, the transactor function must return some transactable data, or throw an exception.

(let [new-release (d/function {:lang     :clojure
                               :params   '[db opts]
                               :requires '[[datomic.api :as d :refer [q]]]
                               :code     '(let [{:keys [artist-name release-name track-names]} opts
                                                artist-id (q '[:find ?e .
                                                               :in $ ?artist-name
                                                               :where [?e :artist/name ?artist-name]]
                                                             db artist-name)]
                                            (if artist-id
                                              [{:release/name  (str/capitalize release-name)
                                                :release/media [{:medium/tracks (for [t track-names]
                                                                                  {:track/name    t
                                                                                   :track/artists artist-id})}]}]
                                              (throw (IllegalArgumentException. (str "No such artist: " artist-name)))))})]
  @(transact conn
             [{:db/ident :release/new
               :db/fn    new-release}]))

(report-exception
  @(transact conn
             [[:release/new {:artist-name  "Does Not Exist"
                             :release-name "Third Fade"
                             :track-names  "Eenie,Meanie, Minie, Moe"}]]))

;; ----

#_(do
    (defn- private-function
      [db]
      [])

    (d/function {:lang   :clojure
                 :params '[db]
                 :code   '(private-function db)})
    ;; TODO: This should fail here, or when transacted,or when executed.

    )


(def db (d/db conn))

(defn tq
  "Execute a Datomic query and present the result as a Clerk table."
  [query & more]
  (-> (apply q query more)
      vec
      clerk/table))

(tq '[:find (pull ?e [:release/status :release/name {:release/language [:language/name]}])
      :in $
      :where [?e :release/gid]]
    db)
