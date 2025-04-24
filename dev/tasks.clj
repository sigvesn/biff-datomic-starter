(ns tasks
  (:require [com.biffweb.tasks :as tasks]
            [com.biffweb.tasks.lazy.babashka.process :refer [shell]]))

(defn hello
  "Says 'Hello'"
  []
  (println "Hello"))

(defn start-datomic-sqlite
  "Starts the datomic database using docker-compose."
  []
  (apply shell (concat ["docker" "compose" "up" "biff-db"])))

;; Tasks should be vars (#'hello instead of hello) so that `clj -M:dev help` can
;; print their docstrings.
(def custom-tasks
  {"db" #'start-datomic-sqlite})

(def tasks (merge tasks/tasks custom-tasks))
