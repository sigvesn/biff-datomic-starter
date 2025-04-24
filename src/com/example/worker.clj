(ns com.example.worker
  (:require [clojure.tools.logging :as log]
            [com.biffweb :as biff]
            [datomic.api :as d]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* 60 n)) (java.util.Date.)))

(defn print-usage [{:keys [biff/db]}]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [n-users (d/q '[:find (count ?user) .
                       :where
                       [?user :user/email _]]
                     db)]
    (log/info "There are" n-users "users.")))

(defn alert-new-user [_ {:keys [db-after db-before tx-data]}]
  (when-let [eid (d/q '[:find ?e .
                        :in $ [[?e ?a ?v _ ?added]]
                        :where
                        [?a :db/ident :user/email _ ?added]]
                      db-after
                      tx-data)]
    (when-not (:user/email (d/entity db-before eid))
      ;; You could send this as an email instead of printing.
      (log/info "WOAH there's a new user"))))

(defn echo-consumer [{:keys [biff/job] :as ctx}]
  (prn :echo job)
  (when-some [callback (:biff/callback job)]
    (callback job)))

(def module
  {:tasks [{:task #'print-usage
            :schedule #(every-n-minutes 5)}]
   :on-tx alert-new-user
   :queues [{:id :echo
             :consumer #'echo-consumer}]})
