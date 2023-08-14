(ns user
  (:require
    [clj-commons.pretty.repl :as repl]
    [net.lewisship.trace :refer [trace] :as trace]))

(repl/install-pretty-exceptions)

(trace/setup-default)

#_ (set! *print-meta* true)
(set! *warn-on-reflection* true)

(trace :startup true)

(require 'datomic-tutorial.clerk)
