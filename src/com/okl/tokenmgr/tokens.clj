(ns com.okl.tokenmgr.tokens
  (:use com.okl.tokenmgr.zookeeper-storage
        [clojure.string :only (join trim)])
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]))

(def base-path "/applications")
(def storage (get-container))

(defn- coalesce-map [map1 map2]
  "recursively merge maps"
  (merge-with
   #(if(map? %1)
      (coalesce-map %1 %2)
      (if (empty? %2)
        %1
        %2))
   map1
   map2))

(defn- get-type [data]
  "Return the type of the node"
  (let [value (:value data)]
    (if (empty? value)
      ""
      (get (json/read-str value) "type"))))

(defn- token? [data]
  "returns true if the json provided is of type token"
  (= "token" (get-type data)))

(defn- app? [data]
  "returns true if the json provided is of type application"
  (= "application" (get-type data)))

(defn- path->full-path [path]
  "adds the base path back in"
  (if (= "" path)
    base-path
    (str base-path "/" path)))

(defn- app->auto-create-message [app]
  "determines a description for the auto-created nodes"
  (json/write-str
   {:type "application"
    :description (str "Created to make application " app)}))

(defn- token->auto-create-message [token]
  "determines a description for the auto-created nodes"
  (json/write-str
   {:type "application"
    :description (str "Created to make token " token)}))

(defn delete-app [app]
  "deletes a node"
  (.delete storage (join "/" [base-path app])))

(defn create-app [app description]
  "creates/updates an application"
  (let [good-app (trim app)
        item (.get-item storage (path->full-path app))
        old-json (if (empty? item)
                   {}
                   (walk/keywordize-keys (json/read-str (:value item))))
        json (json/write-str
              {:type "application"
               :description description})]
    (if (= "token" (:type old-json))
      (throw (IllegalStateException. (str good-app " already exists as a token"))))
    (.create storage (join "/" [base-path good-app]) json (app->auto-create-message good-app))))

(defn delete-token [token]
  "deletes a node"
  (.delete storage (join "/" [base-path token])))

(defn create-token [token description environment value]
  "creates/updates a token"
  (let [good-token (trim token)
        full-path (join "/" [base-path good-token])
        path  (if (> (.lastIndexOf token "/") -1)
                (subs token 0 (.lastIndexOf token "/"))
                "")
        environment (if (empty? environment) "default" environment)
        good-envt (trim environment)
        item (.get-item storage full-path)
        json (if (empty? item)
               {}
               (walk/keywordize-keys (json/read-str (:value item))))
        new-json {:description description
                  :values {(keyword good-envt) {:value value :source path}}
                  :type "token"}
        combined-json (coalesce-map json new-json)]
    (if (= "application" (:type json))
      ;; error! trying to overwrite an app with a token
      (throw (IllegalStateException. (str good-token " already exists as an application")))
      (.create storage full-path (json/write-str combined-json) (token->auto-create-message good-token)))))

(defn get-apps [path]
  "get all of the applications after a particular path"
  (let [full-path (path->full-path path)]
  (map
   #(hash-map :name (subs (get % :name) (inc (count base-path)))
              :description (get (json/read-str (get % :value)) "description"))
   (filter
    app?
    (.subtree storage full-path)))))

(defn- get-tokens-map [path]
  "get a map of token name to token object for all tokens visible at this path
   visible means at this path or below to the root"
  (merge-with
   coalesce-map
   (if (= (.lastIndexOf path "/") -1)
     (if (empty? path)
       {}
       (get-tokens-map ""))
     (get-tokens-map (subs path 0 (.lastIndexOf path "/"))))
   (apply
    merge
    (map #(hash-map (:name %) %)
         (map
          #(let [pathname (:name %)
                 name (subs pathname (inc (.lastIndexOf pathname "/")))
                 description (get (json/read-str (:value %)) "description")
                 values (get (json/read-str (:value %)) "values")]
             {:name name
              :description description
              :values values
              :path path})
          (let [full-path (path->full-path path)]
            (filter
             token?
             (.get-children storage full-path))))))))

(defn get-app [app]
  "search the tree for a given app name. Throws an error if multiple are found"
  (let [apps (filter
              #(= (:name %) app)
              (get-apps ""))]
    (if (> (count apps) 1)
      (throw (IllegalStateException.
              (str "multiple apps named " app " found.")))
      (first apps))))

(defn- get-value [token envt]
  "get the value for a token for a particular environment"
  (let [values (:values token)]
    (if (contains? values envt)
      (get (get values envt) "value")
      (if (contains? values "default")
        (get (get values "default") "value")
        (throw (IllegalStateException.
                (str "No value for token \"" (:name token) "\" in environment " envt)))))))

(defn get-token-values [app envt my-tokens]
  "get the values for all the tokens in a particular environment. a map of"
  "my-tokens can be passed in to add/override values"
  (let [tokens (get-tokens-map (:name (get-app app)))]
    (merge
     (let [thingy
     (apply merge
            (map #(let [result (hash-map (first %) (get-value (second %) envt))]
                    (log/debug result)
                    result)
                 tokens))]
       (log/debug thingy)
       thingy)
     my-tokens)))

(defn get-tokens [path]
  "get all the tokens in a particular path"
  (vals
   (get-tokens-map path)))
