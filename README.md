# DATOMIC TUTORIAL

## Setup and Operation

One time download and setup (takes a few minutes):

    bin/setup

Start the transactor:

    bin/start-transactor

In a separate window, restore the database (this takes only a few seconds):

    bin/restore-db

Datomic is operated in local dev mode; the H2 database files are in the directory `datomic/data/db`.

Finally, start a REPL; this will start [Clerk](https://github.com/nextjournal/clerk) and open a new browser tab.

In the browser tab, open one of the files under `notebook` such as
`src/datomic_tutorial/notebook/basic_queries.clj`.
