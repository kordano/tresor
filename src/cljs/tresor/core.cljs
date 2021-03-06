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

(println "ALL HAIL TO KORDANO!")

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))


(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params] (:db-after (d/transact old params)))
              (fn [old params] (:db-after (d/transact old params)))})


; we can do this runtime wide here, since we only use this datascript version
(read/register-tag-parser! 'datascript/DB datascript/db-from-reader)
(read/register-tag-parser! 'datascript/Datom datascript/datom-from-reader)



;; --- random string generator ---

(def some-chars (map char (range 33 127)))

(defn random-char [] (rand-nth chars))

(defn create-random-string [length]
  (clojure.string/join (vec (take length (repeatedly random-char)))))

(def url-regexp #"(https?|ftp)://[a-z0-9\u00a1-\uffff-]+(\.[a-z0-9\u00a1-\uffff-]+)+(:\d{2,5})?")

;; --- datascript magics ---

(defn get-passwords [stage]
  (let [db (om/value (get-in stage ["eve@tresor.net"  #uuid "11db6582-e734-4464-a710-6a2bfb502229" "master"]))
        query  '[:find ?p ?domain ?username ?password ?url ?user-id ?created-at ?expired
                 :where
                 [?p :domain ?domain]
                 [?p :username ?username]
                 [?p :password ?password]
                 [?p :url ?url]
                 [?p :user-id ?user-id]
                 [?p :created-at ?created-at]
                 [?p :expired ?expired]]]
    (mapv (partial zipmap [:id :domain :username :password :url :user-id :created-at :expired])
          (d/q query db))))


(defn add-password [owner]
  (let [stage (om/get-state owner :stage)
        username (om/get-state owner :username-input-text)
        url (om/get-state owner :url-input-text)
        domain (first (re-find url-regexp (om/get-state owner :url-input-text)))
        password (om/get-state owner :password-input-text)]
    (go
      (<! (s/transact
           stage
           ["eve@tresor.net" #uuid "11db6582-e734-4464-a710-6a2bfb502229" "master"]
           [{:db/id (uuid)
             :domain domain
             :username username
             :password password
             :url url
             :user-id (uuid "kordano@topiq.es")
             :created-at (js/Date.)
             :expired (js/Date. 2015 0 0)}]
           '(fn [old params] (:db-after (d/transact old params)))))
      (<! (s/commit! stage {"eve@tresor.net" {#uuid "11db6582-e734-4464-a710-6a2bfb502229" #{"master"}}})))))


(defn handle-text-change [e owner text]
  (om/set-state! owner text (.. e -target -value)))


;; --- html templating ---

(deftemplate navbar "templates/navbar.html"
  [app owner]
  {[:#nav-input-field] (set-attr :placeholder "Search me ...")})


(defsnippet password "templates/passwords.html" [:.pw-item]
  [pw owner]
  {[:.pw-domain] (content (:domain pw))
   [:.pw-username] (content (:username pw))
   [:.pw-password] (content (apply str (repeat (count (:password pw)) "*")))
   [:.pw-url] (content (apply str (take 20 (:url pw))))})


(deftemplate passwords "templates/passwords.html"
  [app owner state]
  {[:#password-list] (content (map #(password % owner) (get-passwords app)))
   [:#pw-url-input] (do-> (set-attr :value (:url-input-text state))
                             (listen :on-change #(handle-text-change % owner :url-input-text)))
   [:#pw-username-input] (do-> (set-attr :value (:username-input-text state))
                               (listen :on-change #(handle-text-change % owner :username-input-text)))
   [:#pw-password-input] (do-> (set-attr :value (:password-input-text state))
                               (listen :on-change #(handle-text-change % owner :password-input-text)))
   [:#modal-new-pw-btn] (listen :on-click #(do
                                             (add-password owner)
                                             (om/set-state! owner :url-input-text "")
                                             (om/set-state! owner :username-input-text "")
                                             (om/set-state! owner :password-input-text "")))})



;; --- views ---

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

  (defn password-view
    "Main list view showing domain, account, password, creation and expiry date"
    [app owner]
    (reify
      om/IInitState
      (init-state [_]
        {:url-input-text ""
         :username-input-text ""
         :stage stage
         :password-input-text ""})
      om/IRenderState
      (render-state [this state]
        (passwords app owner state))))

  (om/root
   #(navbar %1 %2)
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "main-navbar"))})


  (om/root
   password-view
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "center-container"))})

  )



(comment




 )
