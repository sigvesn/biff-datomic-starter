# Datomic Biff starter project

This is the starter project for [Biff](https://biffweb.com/) using datomic instead of XTDB.

The process for replacing XTDB with Datomic is similar to the process for replacing Postgres with Datomic outlined in https://biffweb.com/p/how-to-use-postgres-with-biff/

Notes on some of the steps:

###### 2
Datomic is run with sqlite as storage via the [datomic-pro-sqlite](https://github.com/filipesilva/datomic-pro-sqlite/) container.

The container can be run via a docker-compose configuration in the `docker-compose.yml`-file is provided. A separate `transactor.properties`-file
is also used to avoid clashing with the port number of any other running datomic instances. If this is not a concern to you, the container can also be run
directly from docker if you want.

Instead of a malli schema, a corresponding datomic schema defined in `resources/schema.edn` and applied
with the [conformity](https://github.com/avescodes/conformity) library.
Datomic schema are transacted into the database, and does not need to be passed with every connection as with XTDB.

The task `clj -M:dev db` is added to start the container, which should be done in addition to running the app in dev or production mode.

###### 3
The datomic uri is set in `config.edn`. `dev/repl.clj` examples are updated with corresponding datomic examples.

Datomic setup and usage is in `com.example.datomic`

###### 4/5
Authentication module is implemented. Since the datomic approach is more similar to XTDB than the postgres setup, we are able to re-use some of the existing module.

###### 6
The datomic [tx-report-queue](https://docs.datomic.com/clojure/index.html#datomic.api/tx-report-queue) is used as a transaction listener. This way we can get a listener-based implementation of the example app as in the original XTDB example.

Tests are implemented using a in-memory datomic database.
