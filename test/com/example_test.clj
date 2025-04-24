(ns com.example-test
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.example.auth-module :as auth-module]
            [com.example.app :as app]
            [com.example.datomic :as datomic]
            [rum.core :as rum]
            [datomic.api :as d]))

(deftest example-test
  (is (= 4 (+ 2 2))))

(def test-user
  #:user{:email "test@user"
         :foo   "bar"
         :bar   "baz"})

(def ^:dynamic *context* nil)

(defmacro with-db
  [& body]
  `(let [context# (datomic/use-datomic {:biff.datomic/uri "datomic:mem://example"})
         context# (assoc context# :biff/db (d/db (:datomic/conn context#)))]
     (binding [*context* context#]
       (try ~@body
            (finally
              (when-let [conn# (:datomic/conn *context*)]
                (d/delete-database (:biff.datomic/uri *context*))))))))

(deftest send-message-test
  (with-db
    (let [message "Test message"
          _       (datomic/submit-tx (:datomic/conn *context*) test-user)
          user-id (auth-module/get-user-id (d/db (:datomic/conn *context*)) (:user/email test-user))
          _       (is (some? user-id))
          ctx     (assoc *context* :session {:uid user-id})
          _       (app/send-message ctx {:text (cheshire/generate-string {:text message})})
          doc     (d/q '[:find ?uid .
                         :in $ ?text
                         :where
                         [?e :msg/text ?text]
                         [?e :msg/user ?uid]]
                       (d/db (:datomic/conn *context*))
                       message)]
      (is (some? doc))
      (is (= doc (:uid (:session ctx)))))))

(deftest chat-test
  (with-db
    (let [messages (for [message ["Test message 1"
                                  "Test message 2"
                                  "Test message 3"]]
                     #:msg{:user "user"
                           :text message
                           :sent-at :db/now})
          _        (datomic/submit-tx (:datomic/conn *context*) (into [(assoc test-user :db/id "user")] messages))
          response (app/chat {:biff/db (d/db (:datomic/conn *context*))})
          html     (rum/render-html response)]
      (is (str/includes? html "Messages sent in the past 10 minutes:"))
      (is (not (str/includes? html "No messages yet.")))
      ;; If you add Jsoup to your dependencies, you can use DOM selectors instead of just regexes:
      ;(is (= n-messages (count (.select (Jsoup/parse html) "#messages > *"))))
      (is (= (count messages) (count (re-seq #"init send newMessage to #message-header" html))))
      (is (every? #(str/includes? html (:msg/text %)) messages)))))
