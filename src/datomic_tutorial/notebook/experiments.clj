;; # Experiments

;; This is a place to try out things; examples here may move to proper tutorial pages.


(ns datomic-tutorial.notebook.experiments
  {:nextjournal.clerk/toc true}
  (:require [datomic-tutorial.conn :as conn]
            [datomic-tutorial.common :refer [report-exception tq transact]]
            [datomic.api :as d]
            [nextjournal.clerk :as clerk]))


;; ## Some Setup

;; Clear the cache before evaluating the whole page; this prevents some runtime errors due to conflicts.
;; If you see an error on this page, just re-run the "open page in Clerk" action.
(clerk/clear-cache!)

;; For these examples, we always start with a fresh, empty, in-memory
;; Datomic database:

(def conn (conn/ephemeral-connection))

(transact conn
          [{:db/ident       :repo/name
            :db/valueType   :db.type/string
            :db/cardinality :db.cardinality/one
            :db/unique      :db.unique/identity}
           {:db/ident       :repo/url
            :db/valueType   :db.type/string
            :db/cardinality :db.cardinality/one}
           {:db/ident       :repo/issues
            :db/valueType   :db.type/ref
            :db/cardinality :db.cardinality/many}
           {:db/ident       :issue/message
            :db/valueType   :db.type/string
            :db/cardinality :db.cardinality/one}])

(transact conn
          [{:db/id     "pedestal"
            :repo/name "pedestal/pedestal"
            :repo/url  "https://github.com/pedestal/pedestal"}
           {:db/id     "pretty"
            :repo/name "clj-commons/pretty"
            :repo/url  "https://github.com/clj-commons/pretty"}])


;; Find them all
(defn find-all
  [db]
  (tq '[:find (pull ?id [*])
        :where
        [?id :repo/name ?name]
        [?id :repo/url ?url]]
      db))

(find-all (d/db conn))

;; ## Can you upsert via :db/id ?

(report-exception
  @(d/transact conn
               [{:db/id    [:repo/name "hlship/trace"]
                 :repo/url "https://github.com/hlship/trace"}]))


;; ## Can you rename a unique attribute's value?

(def after-1 (transact conn
                       [{:db/id     [:repo/name "clj-commons/pretty"]
                         :repo/name "clj-commons/pretty-2"
                         :repo/url  "https://github.com/clj-commons/pretty"}]))

(find-all after-1)

;; Yes, you can; you should see `clj-commons/pretty-2` there.


;; ## How to add a new child

(defn repo-and-issues
  [db repo-name]
  (clerk/table
    (d/q '[:find ?repo-id ?repo-name ?issue-id ?issue-message
           :keys :id :name :issue-id :message
           :in $ ?repo-name
           :where
           [?repo-id :repo/name ?repo-name]
           [?repo-id :repo/issues ?issue-id]
           [?issue-id :issue/message ?issue-message]]
         db repo-name)))

(def after-2 (transact conn
                       [{:db/id       [:repo/name "pedestal/pedestal"]
                         :repo/issues {:db/id         "new-issue"
                                       :issue/message "first issue"}}]))

(repo-and-issues after-2 "pedestal/pedestal")

;; Now add a new issue to the repo:

(def after-3 (transact conn
                       [{:db/id       [:repo/name "pedestal/pedestal"]
                         :repo/issues {:db/id         "new-issue"
                                       :issue/message "second issue"}}]))

(repo-and-issues after-3 "pedestal/pedestal")

;; Another way:

(def after-4 (transact conn
                       [{:repo/_issues  [:repo/name "pedestal/pedestal"]
                         :issue/message "third issue"}]
                       ))

(repo-and-issues after-4 "pedestal/pedestal")

;; Can you set an attribute to nil?  Nope, you get :db.error/nil-value Nil is not a legal value

(let [db       (d/db conn)
      result   (d/q '[:find [?issue-id ?message]
                      :in $ ?repo-name
                      :where [?repo-id :repo/name ?repo-name]
                      [?repo-id :repo/issues ?issue-id]
                      [?issue-id :issue/message ?message]]
                    db "pedestal/pedestal")
      [issue-id message] #trace/result result
      db-after (transact conn
                         [[:db/retract issue-id :issue/message message]])]
  (repo-and-issues db-after "pedestal/pedestal"))

