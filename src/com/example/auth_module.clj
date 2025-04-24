(ns com.example.auth-module
  "Re-implementation of Biff's auth module using Datomic instead of XTDB.
  Uses some of the same functions as the original Biff auth module directly
  with the XTDB fns rebound to Datomic and re-implements others from scratch."
  (:require [com.biffweb :as biff]
            [com.biffweb.impl.auth :as auth]
            [com.example.datomic :as datomic]
            [datomic.api :as d]))

(defn send-code-handler [{:keys [datomic/conn params]
                          :as ctx}]
  (let [{:keys [success error email code]} (auth/send-code! ctx)]
    (when success
      (datomic/submit-tx conn {:auth.code/code code
                               :auth.code/email email
                               :auth.code/created-at :db/now
                               :auth.code/failed-attempts 0}))
    {:status 303
     :headers {"location" (if success
                            (str "/verify-code?email=" (:email params))
                            (str (:on-error params "/") "?error=" error))}}))

(defn verify-code-handler [{:keys [biff.auth/app-path
                                   biff.auth/new-user-tx
                                   biff.auth/get-user-id
                                   biff.xtdb/node
                                   biff/db
                                   params
                                   session]
                            :as ctx}]
  (let [email (biff/normalize-email (:email params))
        code  (d/entity db [:auth.code/email email])
        success (and (auth/passed-recaptcha? ctx)
                     (some? code)
                     (< (:auth.code/failed-attempts code) 3)
                     (not (biff/elapsed? (:auth.code/created-at code) :now 3 :minutes))
                     (= (:code params) (:auth.code/code code)))
        existing-user-id (when success (get-user-id db email))
        tx (cond
             success
             (into [[:db/retractEntity (:db/id code)]]
                   (when-not existing-user-id
                     [(new-user-tx ctx email)]))

             (and (not success)
                  (some? code)
                  (< (:biff.auth.code/failed-attempts code) 3))
             [:add code :auth.code/failed-attempts 1])]
    (datomic/submit-tx node tx)
    (if success
      {:status 303
       :headers {"location" app-path}
       :session (assoc session :uid (or existing-user-id
                                        (get-user-id (d/db node) email)))}
      {:status 303
       :headers {"location" (str "/verify-code?error=invalid-code&email=" email)}})))

(defn signout [{:keys [session]}]
  {:status 303
   :headers {"location" "/"}
   :session (dissoc session :uid)})

(defn new-user-tx [_ctx email]
  [{:user/email email
    :user/joined-at (java.util.Date.)}])

(defn get-user-id [db email]
  (d/entid db [:user/email email]))

(def default-options
  #:biff.auth{:app-path "/app"
              :invalid-link-path "/signin?error=invalid-link"
              :check-state true
              :new-user-tx new-user-tx
              :get-user-id get-user-id
              :single-opt-in false
              :email-validator auth/email-valid?})

(defn wrap-options [handler options]
  (fn [ctx]
    (handler (assoc (merge options ctx)
                    :biff.xtdb/node (:datomic/conn ctx)))))

(intern 'xtdb.api 'db d/db)
(intern 'com.biffweb.impl.xtdb 'submit-tx (fn [{:keys [datomic/conn]} tx]
                                            @(d/transact conn tx)))


(def module
  {:routes [["/auth" {:middleware [[wrap-options default-options]]}
             ["/send-link"          {:post auth/send-link-handler}]
             ["/verify-link/:token" {:get  auth/verify-link-handler}]
             ["/verify-link"        {:post auth/verify-link-handler}]
             ["/send-code"          {:post send-code-handler}]
             ["/verify-code"        {:post verify-code-handler}]
             ["/signout"            {:post signout}]]]})
