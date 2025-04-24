(ns repl
  (:require [com.example :as main]
            [datomic.api :as d]
            [com.biffweb :as biff]))

;; REPL-driven development
;; ----------------------------------------------------------------------------------------
;; If you're new to REPL-driven development, Biff makes it easy to get started: whenever
;; you save a file, your changes will be evaluated. Biff is structured so that in most
;; cases, that's all you'll need to do for your changes to take effect. (See main/refresh
;; below for more details.)
;;
;; The `clj -M:dev dev` command also starts an nREPL server on port 7888, so if you're
;; already familiar with REPL-driven development, you can connect to that with your editor.
;;
;; If you're used to jacking in with your editor first and then starting your app via the
;; REPL, you will need to instead connect your editor to the nREPL server that `clj -M:dev
;; dev` starts. e.g. if you use emacs, instead of running `cider-jack-in`, you would run
;; `cider-connect`. See "Connecting to a Running nREPL Server:"
;; https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server
;; ----------------------------------------------------------------------------------------

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/merge-context @main/system))

(defn check-config []
  (let [prod-config (biff/use-aero-config {:biff.config/profile "prod"})
        dev-config  (biff/use-aero-config {:biff.config/profile "dev"})
        ;; Add keys for any other secrets you've added to resources/config.edn
        secret-keys [:biff.middleware/cookie-secret
                     :biff/jwt-secret
                     :mailersend/api-key
                     :recaptcha/secret-key
                     ; ...
                     ]
        get-secrets (fn [{:keys [biff/secret] :as config}]
                      (into {}
                            (map (fn [k]
                                   [k (secret k)]))
                            secret-keys))]
    {:prod-config prod-config
     :dev-config dev-config
     :prod-secrets (get-secrets prod-config)
     :dev-secrets (get-secrets dev-config)}))

(comment
  (defn conn [] (:conn (get-context)))
  (defn db [] (d/db (conn)))

  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, config.env, or deps.edn.
  (main/refresh)

  ;; If you edit the schema data (in resources/schema.edn), you can reset the
  ;; database by running `rm -r storage` (DON'T run that in prod), and
  ;; restarting your app. You do not need to do this for addidive schema changes
  ;; (the fixtures themselves have been moved to resources/fixtures.edn).


  ;; query
  (d/q '[:find ?foo .
         :in $ ?email
         :where
         [?user :user/email ?email]
         [?user :user/foo ?foo]]
       (db)
       "a@example.com")

  (d/q '[:find ?email
         :where
         [?user :user/email ?email]]
       (db))

  (d/q '[:find ?foo
         :where
         [?user :user/email ?email]
         [?user :user/foo ?foo]]
       (d/history (db)))

  ;; Transactions
  (d/transact (conn) [{:db/id [:user/email "b@example.com"]
                       :user/foo "bar"}])
  (d/transact (conn) [[:db/retractEntity [:user/email "b@example.com"]]])
  ;; pull syntax
  (d/pull (db) '[*] [:user/email "b@example.com"])


  ;; Auth module
  (d/transact (conn) [{:auth.code/email "a@example.com"
                     :auth.code/failed-attempts 1}])
  (get (d/entity (db) [:auth.code/email "a@example.com"]) :auth.code/failed-attempts)
  (d/transact (conn) [[:add-num [:auth.code/email "a@example.com"] :auth.code/failed-attempts 1]])

  ;; Queue
  (sort (keys (get-context)))
  (:datomic/monitor (get-context))
  (:com.example/chat-clients (get-context))

  ;; Check the terminal for output.
  (biff/submit-job (get-context) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-context) :echo {:foo "bar"})))
