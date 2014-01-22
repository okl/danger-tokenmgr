(ns com.okl.tokenmgr.config
  "Cofiguration provider for the tokenmanager"
  {:author "Eric Sayle"
   :date "01/17/2014"}
  (:require [clojure.tools.logging :as log]
            [clj-yaml.core :as yaml]
            [roxxi.utils.collections :as coll]
            [roxxi.utils.print :refer [print-expr]]))

(defrecord StorageConfig [host port])

(defrecord WebConfig [prefix])

(defn- read-yaml-configuration [filename]
  (yaml/parse-string (slurp filename)))

(defn- yaml-configuration-for-name [configuration]
  (coll/project-map configuration
                    :key-xform name))

(defn- yaml-file->storage-config [filename]
  (let [configurations (yaml-configuration-for-name (read-yaml-configuration filename))]
    (coll/project-map configurations
                      :value-xform #(->StorageConfig (:host %)
                                                     (:port %)))))

(defn- yaml-file->web-config [filename]
  (let [configurations (yaml-configuration-for-name (read-yaml-configuration filename))]
    (coll/project-map configurations
                      :value-xform #(->WebConfig (:prefix %)))))

(defprotocol TokenmgrConfigurationBroker
  (storage-configuration [_] "Returns a StorageConfig object for the storage")
  (web-configuration [_] "Returns a WebConfig object for the web information"))

(deftype YamlConfigurationBroker [yaml-file]
  TokenmgrConfigurationBroker
  (storage-configuration [_]
    (get (yaml-file->storage-config yaml-file) "storage"))
  (web-configuration [_]
    (get (yaml-file->web-config yaml-file) "web")))

(defn make-yaml-config-broker [yaml-file]
  (YamlConfigurationBroker. yaml-file))
