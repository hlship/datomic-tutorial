# DATOMIC TUTORIAL

## Setup

Follow the [Keith's Instructions](https://nubank.atlassian.net/wiki/spaces/DAT/pages/262853820615/How+To+Easily+Run+Datomic+Locally)
to download Datomic Pro and the MBrainz database; note that *Datomic is Now Free* and the Datomic transactor can be downloaded
from without licensing; see [Get Datomic](https://docs.datomic.com/pro/getting-started/get-datomic.html) for the latest version.

Setup steps:

* Make sure you are using Java 11 or better
* `curl https://datomic-pro-downloads.s3.amazonaws.com/1.0.6733/datomic-pro-1.0.6733.zip -O`
* `unzip datomic-pro-1.0.6733.zip -d .`
* `ln -sf datomic-pro-1.0.6733 datomic`
* `curl -s https://s3.amazonaws.com/mbrainz/datomic-mbrainz-1968-1973-backup-2017-07-20.tar | tar xv`

Start the transactor:

    bin/start-transactor

In a separate window, restore the database (this takes only a few seconds):

    bin/restore-db

Datomic is operated in local dev mode; the H2 database files are in the directory `datomic/data/db`.


Finally, start a REPL; this will start [Clerk](https://github.com/nextjournal/clerk) and open a new browser tab.

_More to come, but next version of Clerk will simplify getting to one of the pages._
