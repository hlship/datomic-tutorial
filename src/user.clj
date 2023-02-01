(ns user
  (:require [net.lewisship.trace :refer [trace] :as trace]))

(require 'io.aviso.logging.setup)

(trace/setup-default)

#_ (set! *print-meta* true)
(set! *warn-on-reflection* true)

(trace :startup true)
