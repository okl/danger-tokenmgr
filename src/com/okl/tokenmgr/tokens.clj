(ns com.okl.tokenmgr.tokens
  (:use com.okl.tokenmgr.zookeeper-storage
        [clojure.string :only (join trim)])
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]))

(def base-path "/applications")
(def storage (get-container))

(defn- read-json-str [data]
  "try to read json data, and error with the bad json if there's an error"
  (try
    (json/read-str data)
    (catch Exception e
      (throw (IllegalStateException. (str "Error processsing " data ":\n" (.getMessage e)))))))

(defn coalesce-map [map1 map2]
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
      (get (read-json-str value) "type"))))

(defn- token? [data]
  "returns true if the json provided is of type token"
  (= "token" (get-type data)))

(defn- app? [data]
  "returns true if the json provided is of type application"
  (try
    (= "application" (get-type data))
    (catch IllegalStateException e
       (throw (IllegalStateException. (str "Unable to determine if " data " is an app:\n" (.getMessage e)))))))

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
                   (walk/keywordize-keys (read-json-str (:value item))))
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
               (walk/keywordize-keys (read-json-str (:value item))))
        new-json {:description description
                  :values {(keyword good-envt) {:value value :source path}}
                  :type "token"}
        combined-json (coalesce-map json new-json)]
    (if (= "application" (:type json))
      ;; error! trying to overwrite an app with a token
      (throw (IllegalStateException. (str good-token " already exists as an application")))
      (.create storage full-path (json/write-str combined-json) (token->auto-create-message good-token)))))

(defn- get-app-from-map [app-map]
  (let [name (subs (get app-map :name) (inc (count base-path)))
        description (get (read-json-str (get app-map :value)) "description")
        description (if (nil? description) "" description)]
    (hash-map :name name
              :description description)))

(defn get-apps [path]
  "get all of the applications after a particular path"

  (if-not (.get-item storage base-path)
    (.create storage base-path "" ""))
  (let [full-path (path->full-path path)]
  (map
   get-app-from-map
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
                 description (get (read-json-str (:value %)) "description")
                 values (get (read-json-str (:value %)) "values")]
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

(defn get-value [token envt]
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

(defn denormalize-token [token]
  (let [name (:name token)
        description (:description token)]
    (map (fn [x]
           {:name name
            :description description
            :environment x
            :value (get (get (:values token) x) "value")
            :source (get (get (:values token) x) "source")})
         (keys (:values token)))))

(defn denormalize-tokens [tokens]
  (apply concat (map denormalize-token tokens)))

(defn delete-value [pathname envt]
  (log/info (str "Trying to delete " pathname " " envt))
  (let [full-path (path->full-path pathname)
        json (:value (.get-item storage full-path))
        token (read-json-str json)
        values (get token "values")
        new-values (into {} (remove #(= envt (first %)) values))
        new-token {:description (get token "description")
                   :values new-values
                   :type "token"}
        new-json (json/write-str new-token)]
    (log/info (str "Json is " json))
    (log/info (str "I changed " values))
    (log/info (str "into " (apply str new-values)))
    (log/info new-json)
    (if (empty? new-values)
      (.delete storage full-path)
      (.create storage full-path new-json ""))))
