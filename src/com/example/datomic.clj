(ns com.example.datomic
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]))

;; Re-implements the add function from Biff's XTDB module
(def add-fn
  {:db/ident :add
   :db/fn (d/function
            {:lang "clojure"
             :params '[db e a v]
             :requires '[[datomic.api :as d]]
             :code '(let [current-value (or (get (d/entity db e) a) 0)]
                      [[:db/add e a (+ current-value v)]])})})

(defn bootstrap-schema! [conn resource]
  (let [res (c/read-resource resource)
        all-keys (vec (sort (keys res)))]
    (c/ensure-conforms conn res all-keys)))

(defn use-datomic
  [{:biff.datomic/keys [uri] :as ctx}]
  (d/create-database uri)
  (let [conn (d/connect uri)]
    (bootstrap-schema! conn "schema.edn")
    @(d/transact conn [add-fn])
    (assoc ctx :datomic/conn conn)))

(defn change-monitor [ctx change-queue on-txes]
  (while true
    (let [report (.take change-queue)]
      (doseq [tx on-txes]
        (tx ctx report)))))

(defn use-tx-listener
  "Re-implements Biff's XTDB tx-listener using datomics tx-report-queue"
  [{:keys [biff/modules datomic/conn] :as ctx}]
  (if-not modules
    ctx
    (let [on-txes (filter fn? (keep :on-tx @modules))
          monitor (future (change-monitor ctx (d/tx-report-queue conn) on-txes))]
      (-> (assoc ctx
                 :datomic/queue (d/tx-report-queue conn)
                 :datomic/monitor monitor)
          (update :biff/stop conj #(some-> conn d/remove-tx-report-queue))
          (update :biff/stop conj #(cond-> monitor future? future-cancel))))))

(defn assoc-db
  [ctx]
  (assoc ctx :biff/db (d/db (:datomic/conn ctx))))

;; Re-implements Biff's :db/now
(defn set-now
  [now tx]
  (walk/postwalk #(if (= % :db/now) now %) tx))

(defn wrap-vec
  [tx]
  (if (vector? tx)
    tx
    [tx]))

(defn submit-tx
  [conn tx]
  (try @(d/transact conn (->> tx
                              (set-now (java.util.Date.))
                              wrap-vec))
       (catch Exception e
         (log/error e)
         (throw e))))
