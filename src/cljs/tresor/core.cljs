(ns tresor.core
  (:require [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [hasch.core :refer [uuid]]
            [datascript :as d]
            [geschichte.stage :as s]
            [geschichte.sync :refer [client-peer]]
            [geschichte.auth :refer [auth]]
            [konserve.store :refer [new-mem-store]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [om.dom :as omdom])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

(println "ALL HAIL TO KONNY!")

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

;; fire up repl
#_(do
    (ns weasel.startup)
    (require 'weasel.repl.websocket)
    (cemerick.piggieback/cljs-repl
        :repl-env (weasel.repl.websocket/repl-env
                   :ip "0.0.0.0" :port 17782)))


;; weasel websocket
(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
     :jsload-callback (fn [] (print "reloaded")))
    (ws-repl/connect "ws://localhost:17782" :verbose true)))


(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params] (:db-after (d/transact old params)))
              (fn [old params] (:db-after (d/transact old params)))})


; we can do this runtime wide here, since we only use this datascript version
(read/register-tag-parser! 'datascript/DB datascript/db-from-reader)
(read/register-tag-parser! 'datascript/Datom datascript/datom-from-reader)


(def trusted-hosts (atom #{:geschichte.stage/stage (.getDomain uri)}))

(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@polyc0l0r.net" "lisp"}))


;; --- random string generator ---

(def some-chars (map char (range 33 127)))

(defn random-char [] (rand-nth chars))

(defn create-random-string [length]
  (clojure.string/join (vec (take length (repeatedly random-char)))))


;; --- datascript magics ---

(defn get-passwords [stage]
  (let [db (om/value (get-in stage ["eve@tresor.net"  #uuid "11db6582-e734-4464-a710-6a2bfb502229" "master"]))
        query  '[:find ?p ?domain ?username ?password ?user-id ?created-at ?expired
                 :where
                 [?p :domain ?domain]
                 [?p :username ?username]
                 [?p :password ?password]
                 [?p :user-id ?user-id]
                 [?p :created-at ?created-at]
                 [?p :expired ?expired]]]
    (mapv (partial zipmap [:id :domain :username :password :user-id :created-at :expired])
         (d/q query db))))


;; --- html templating ---

(defsnippet password "templates/passwords.html" [:.pw-item]
  [pw owner]
  {[:.pw-domain] (content (:domain pw))
   [:.pw-username] (content (:username pw))
   [:.pw-password] (content (:password pw))
   [:.pw-created-at] (content (.toLocaleString (:created-at pw)))
   [:.pw-expired] (content (.toLocaleString (:expired pw)))})


(deftemplate passwords "templates/passwords.html"
  [app owner]
  {[:#password-list] (content (map #(password % owner) (get-passwords app)))})


;; --- views ---

(defn password-view
  "Main list view showing domain, account, password, creation and expiry date"
  [app owner]
  (reify
    om/IRender
    (render [this]
      (passwords app owner))))


;; --- *hust* (start-all-services) *hust* ---

(go
  (def store (<! (new-mem-store
                  (atom
                   (read-string
                    "{#uuid \"10b9b659-16b9-5731-b138-81b81cb7db05\" #datascript/DB {:schema {:passwords {:db/cardinality :db.cardinality/many}, :domains {:db/cardinality :db.cardinality/many}}, :datoms []}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), #uuid \"1400dd5a-eee8-5edc-b12e-cbba25429ba0\" {:transactions [[#uuid \"10b9b659-16b9-5731-b138-81b81cb7db05\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-07-29T10:52:38.572-00:00\", :author \"eve@tresor.net\"}, \"eve@tresor.net\" {#uuid \"11db6582-e734-4464-a710-6a2bfb502229\" {:description \"tresor security services.\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :pull-requests {}, :causal-order {#uuid \"1400dd5a-eee8-5edc-b12e-cbba25429ba0\" []}, :public false, :branches {\"master\" #{#uuid \"1400dd5a-eee8-5edc-b12e-cbba25429ba0\"}}, :head \"master\", :last-update #inst \"2014-07-29T10:52:38.572-00:00\", :id #uuid \"11db6582-e734-4464-a710-6a2bfb502229\"}}}")
                   (atom {'datascript/Datom datascript/datom-from-reader
                          'datascript/DB datascript/db-from-reader})))))

  (def peer (client-peer "CLIENT-PEER" store (partial auth store auth-fn (fn [creds] nil) trusted-hosts)))

  (def stage (<! (s/create-stage! "eve@tresor.net" peer eval-fn)))

  (<! (s/subscribe-repos! stage
                          {"eve@tresor.net"
                           {#uuid "11db6582-e734-4464-a710-6a2bfb502229"
                            #{"master"}}}))

  (<! (s/connect!
       stage
       (str
        (if ssl?  "wss://" "ws://")
        (.getDomain uri)
        (when (= (.getDomain uri) "localhost")
          (str ":" 8085 #_(.getPort uri)))
        "/geschichte/ws")))

  (om/root
   password-view
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "center-container"))})

  )



(comment

  (go
    (<! (s/transact
         stage
         ["eve@tresor.net" #uuid "11db6582-e734-4464-a710-6a2bfb502229" "master"]
         (concat [{:db/id (uuid)
                   :domain "https://accounts.google.com"
                   :username "fuerstgivo"
                   :password "56789"
                   :user-id "kordano@topiq.es"
                   :created-at (js/Date.)
                   :expired (js/Date. 2014 7 29)}]
                 [{:db/id (uuid)
                   :domain "https://accounts.google.com"
                   :username "konnykuehne"
                   :password "123456"
                   :user-id "kordano@topiq.es"
                   :created-at (js/Date.)
                   :expired (js/Date. 2014 7 29)}])
         '(fn [old params] (:db-after (d/transact old params)))))
    (<! (s/commit! stage {"eve@tresor.net" {#uuid "11db6582-e734-4464-a710-6a2bfb502229" #{"master"}}})))


  (let [db (get-in (-> @stage :volatile :val-atom deref) ["eve@tresor.net"  #uuid "11db6582-e734-4464-a710-6a2bfb502229" "master"])
        query  '[:find ?p ?domain ?username ?password ?user-id ?created-at ?expired
                 :where
                 [?p :domain ?domain]
                 [?p :username ?username]
                 [?p :password ?password]
                 [?p :user-id ?user-id]
                 [?p :created-at ?created-at]
                 [?p :expired ?expired]]]
    (map (partial zipmap [:id :domain :username :password :user-id :created-at :expired])
         (d/q query db)))

  (-> @stage :volatile :peer deref :volatile :store :state deref)

  (-> @stage :volatile :val-atom deref)


 )
