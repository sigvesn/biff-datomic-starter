(ns com.example
  (:require [com.biffweb :as biff]
            [com.example.email :as email]
            [com.example.app :as app]
            [com.example.home :as home]
            [com.example.middleware :as mid]
            [com.example.ui :as ui]
            [com.example.worker :as worker]
            [com.example.datomic :as datomic]
            [com.example.auth-module :as auth-module]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [nrepl.cmdline :as nrepl-cmd])
  (:gen-class))

(def modules
  [app/module
   auth-module/module
   home/module
   worker/module])

(def routes [["" {:middleware [mid/wrap-site-defaults]}
              (keep :routes modules)]
             ["" {:middleware [mid/wrap-api-defaults]}
              (keep :api-routes modules)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 mid/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (test/run-all-tests #"com.example.*-test"))

(def initial-system
  {:biff/merge-context-fn #'datomic/assoc-db
   :biff/modules #'modules
   :biff/send-email #'email/send-email
   :biff/handler #'handler
   :biff.beholder/on-save #'on-save
   :biff.middleware/on-error #'ui/on-error
   :com.example/chat-clients (atom #{})})

(defonce system (atom {}))

(def components
  [biff/use-aero-config
   datomic/use-datomic
   biff/use-queues
   datomic/use-tx-listener
   biff/use-htmx-refresh
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "System started.")
    (log/info "Go to" (:biff/base-url new-system))
    new-system))

(defn -main []
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
