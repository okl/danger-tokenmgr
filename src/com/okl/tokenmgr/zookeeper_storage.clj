(ns com.okl.tokenmgr.zookeeper-storage
  (:use [clojure.string :only (join)]
        com.okl.tokenmgr.storage)
  (:require [zookeeper :as zk]
            [com.okl.tokenmgr.config :as config]))

(defn- zk-string []
  (let [broker (config/make-yaml-config-broker "conf/tokenmgr.yml")
        config (.storage-configuration broker)
        string (join ":" [(:host config) (:port config)])]
    string))

(def my-connection (atom (zk/connect (zk-string))))

;; obtain a client; will reconnect if the client has closed
(defn- get-client []
  (if (= :CLOSED (zk/state @my-connection))
    (swap! my-connection (fn[x] (zk/connect (zk-string)))))
  @my-connection)

;; function for running a zookeeper operation
;; private function
(defn- zk-op [op path]
  (let [client (get-client)]
    (op client path)))

;; function for setting the data in zookeeper
(defn- zk-set [path value]
  (let [client (get-client)
        version (:version (zk/exists client path))
        data (if value
               (.getBytes value "UTF-8")
               value)
        result (zk/set-data client path data version)]
    (zk/close client)
    result))

(deftype ZookeeperStorageContainer []
  StorageContainer
  (get-children [this path]
    (map
     #(.get-item this (str path "/" %))
     (zk-op zk/children path)))

  (get-item [this path]
    (when (zk-op zk/exists path)
    (let [name path
          data (:data (zk-op zk/data path))
          value (if data
                  (String. data "UTF-8")
                  ())
          children (zk-op zk/children path)]
      {:name name
       :value value
       :children children})))

  (delete [this path]
    (when (zk-op zk/exists path)
      (zk-op zk/delete-all path)))

  (create [this path data autocreate-message]
    (let [client (get-client)
          last-slash (.lastIndexOf path "/")
          parent (subs path 0 last-slash)]
      (if (and (pos? (count parent))
               (not (zk-op zk/exists parent)))
        (.create this parent autocreate-message autocreate-message))
      (zk/create client path :persistent? true)
      (zk/close client)
      (zk-set path data)))

  (subtree [this path]
    (remove
     empty?
     (apply concat
      ;; this row
      (map #(.get-item this (str path "/" %))
           (zk-op zk/children path))
      ;; recursive call
      (map #(.subtree this (str path "/" %))
           (zk-op zk/children path))))))

(defn get-container []
  (ZookeeperStorageContainer. ))
