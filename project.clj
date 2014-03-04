(defproject com.okl.tokenmgr "1.0.1"
  :description "Store tokens in zookeeper and filter them"
  :url "https://github.com/okl/danger-tokenmgr"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [zookeeper-clj "0.9.3"]
                 [ring/ring-json "0.2.0"]
                 [org.clojure/data.json "0.2.3"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.slf4j/slf4j-simple "1.7.5"]
                 [roxxi/clojure-common-utils "0.0.13"]
                 [clj-yaml "0.4.0"]
                 [clojure-csv/clojure-csv "2.0.1"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler com.okl.tokenmgr.handler/app}
  :main com.okl.tokenmgr.cli
  :resource-paths ["resources" "SlickGrid"]
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]]}})
