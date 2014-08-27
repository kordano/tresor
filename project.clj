(defproject tresor "0.1.0-SNAPSHOT"

  :description "A simple password manager"

  :url "http://example.com/FIXME"


  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/core.memoize "0.5.6" :exclusions [org.clojure/core.cache]]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]

                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [org.clojure/tools.logging "0.3.0"]

                 [http-kit "2.1.19"]
                 [ring "1.3.1"]
                 [com.cemerick/friend "0.2.1"]
                 [enlive "1.1.5"]
                 [compojure "1.1.8"]

                 [prismatic/dommy "0.1.3"]
                 [datascript "0.3.1"]
                 [om "0.7.1"]
                 [kioo "0.4.0"]
                 [figwheel "0.1.3-SNAPSHOT"]
                 [com.facebook/react "0.11.1"]
                 [net.polyc0l0r/geschichte "0.1.0-SNAPSHOT"]

                 [weasel "0.3.0"]]

  :source-paths ["src/cljs" "src/clj"]

  :min-lein-version "2.0.0"

  :main tresor.core

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.3-SNAPSHOT"]]

  :figwheel {:http-server-root "public"
             :port 3449
             :css-dirs ["resources/public/css"]}

  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src/cljs"]
     :compiler {:output-to "resources/public/js/compiled/main.js"
                :output-dir "resources/public/js/compiled/out"
                :optimizations :none
                :source-map true}}
    {:id "prod"
     :source-paths ["src/cljs"]
     :compiler {:output-to "resources/public/js/main.js"
                :optimizations :simple}}]}

  )
