(ns tresor.core
  (:require [clojure.data :refer [diff]]
            [domina :as dom]
            [figwheel.client :as figw :include-macros true]
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
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

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
              '(fn [old params]
                 (:db-after (d/transact old params)))
              (fn [old params]
                (:db-after (d/transact old params)))})



; we can do this runtime wide here, since we only use this datascript version
(read/register-tag-parser! 'datascript/DB datascript/db-from-reader)
(read/register-tag-parser! 'datascript/Datom datascript/datom-from-reader)




(def trusted-hosts (atom #{:geschichte.stage/stage (.getDomain uri)}))


(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@polyc0l0r.net" "lisp"}))

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
        "/geschichte/ws"))))

(comment
  ;; recreate database
  (let [schema {:passwords {:db/cardinality :db.cardinality/many}
                :domains {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go
      (println (<! (s/create-repo! stage
                                   "eve@tresor.net"
                                   "tresor security services."
                                   @conn
                                   "master")))))

  (go (<! (s/transact stage
                      ["eve@tresor.net"
                       "11db6582-e734-4464-a710-6a2bfb502229"

                       ])))

  (-> @stage :volatile :peer deref :volatile :store :state deref)

)
