# DATOMIC TUTORIAL

## Setup

Follow the [Keith's Instructions](https://nubank.atlassian.net/wiki/spaces/DAT/pages/262853820615/How+To+Easily+Run+Datomic+Locally)
to download Datomic Pro and the MBrainz database.

Untar the MBrainz database backup into the current directory.

Create a `datomic` symlink to the Datomic Pro directory.

Create a `transactor.properties` by copying `datomic/config/samples/dev-transactor-template.properties`, then editing it to add the license key.

Start the transactor:

    datomic/bin/transactor `pwd`/transactor.properties

Restore the database (this takes only a few seconds):

    datomic/bin/datomic restore-db file://`pwd`/mbrainz-1968-1973 datomic:dev://localhost:4334/mbrainz-1968-1973

Datomic is operated in local dev mode; the H2 database files are in the directory `datomic/data/db`.


