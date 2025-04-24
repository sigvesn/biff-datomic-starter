# Datomic Biff starter project

This is the starter project for [Biff](https://biffweb.com/) using datomic instead of XTDB.

The process for replacing XTDB with Datomic is similar to the process for replacing Postgres with Datomic outlined in https://biffweb.com/p/how-to-use-postgres-with-biff/

Datomic is run with sqlite as storage via the [datomic-pro-sqlite](https://github.com/filipesilva/datomic-pro-sqlite/) container.

The container is configured to run via docker-compose and configured in the `docker-compose.yml`-file and a separate `transactor.properties`-file to not clash with the port number of other running datomic instances, but can also be run directly if you want.

The task `clj -M:dev db` is added to start the container, which should be done in addition to running the app in dev or production mode.
