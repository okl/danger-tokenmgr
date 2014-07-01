(ns com.okl.tokenmgr.port
  "Provides import/export capabilities for zookeeper token manager"
  {:author "Heli Wang"
   :date "6/26/14"}
  (:import (java.util Date)
           (java.text DateFormat)
           (java.io FileNotFoundException))
  (:require [compojure.core :as core]
            [com.okl.tokenmgr.tokens :as tok]
            [cheshire.core :as js]
            [clojure-csv.core :as csv]
            [clojure.string :as str]))


(defn- dig-tokens
  "Digs for application paths and corresponding tokens"
  []
  (let [apps (tok/get-apps "")]
    {:apps apps
     :tokens (map #(tok/get-tokens (:name %)) apps)}))

(defn- generate-pathname
  "Creates a pathname from a token's name and its path. If path is not, the
provided name only is used."
  ([name] name)
  ([path name] (if (empty? path)
                 name
                 (str/join "/" [path name]))))

(defn- add-token-to-environment!
  "Adds a token to an environment"
  [token-name token-path token-description environment]
  (let [pathname (generate-pathname token-path token-name)
        env-name (name (key environment)) ;; Extracts env name from keyword
        value (:value (val environment))]
    (tok/create-token pathname token-description env-name value)))

(defn- add-token!
  "Adds a token across multiple enviroments"
  [token]
  (dorun (map (partial add-token-to-environment!
                       (:name token)
                       (:path token)
                       (:description token))
              (seq (:values token)))))

(defn- add-app!
  "Adds an app to the zookeeper"
  [app]
  (tok/create-app (generate-pathname (:name app))
                  (:description app)))

(defn- add-app-tokens!
  "Adds tokens for a given app"
  [app tokens]
  (add-app! app)
  (dorun (map add-token! tokens)))

(defn- plant-tokens!
  "Places tokens in corresponding app paths, updating new fields as needed."
  [filedump]
  (let [apps (:apps filedump)
        tokens (:tokens filedump)]
    (if (and (= (count apps) (count tokens))
             (pos? (count apps)))
      (dorun (map add-app-tokens!
                  apps tokens))
      ;;; BUG: doesn't cleanly handle case where imported apps clash with
      ;;; already present tokens, or vice versa. Currently, this is absorbed
      ;;; into generic exception reporting.
      (throw (IllegalStateException.
              (str "Error importing file: App and Token"
                   "counts don't sync.\n"))))))

(defn- store-tokens!
  "Stores token filedump into file"
  [format filename filedump]
  (spit filename (format filedump)))

(defn- load-tokens
  "Loads token filedump from file"
  [format filename]
  ;; true indicates I want keys instantiated as keywords
  (format (slurp filename)))

;; The token structure consists of as follows:

;; APP-TOKEN-CONFIGURATION ::= map(NAME, DESC, ENV-VALUES, PATH)
;; NAME ::= pair("name" : NAME_STRING)
;; DESC ::= pair("description" : DESC_STRING)
;; ENV-VALUES ::= pair("values" : ENVS)
;; ENVS ::= map(ENV...)
;; ENV ::= pair(ENV_STRING : VALUES)
;; VALUES ::= map(VALUE, SOURCE)
;; VALUE ::= pair("value" : VALUE_STRING)
;; SOURCE ::= pair("source" : SOURCE_STRING)
;; PATH ::= pair("path" : PATH_STRING)

(defn- token-in-path?
  "Checks if a token's path is in the app's path"
  [tok-path app-path]
  (string? (re-find (re-pattern (str "^" tok-path)) app-path)))

(defn- valid-env?
  "Checks if given enviroment meets filedump rules"
  [path env]
  (let [env-name (first env)
        env-val (second env)]
    (and (contains? env-val :value)
         (contains? env-val :source)
         (token-in-path? (:source env-val) path))
))

(defn- valid-app?
  "Checks if an app is valid"
  [app]
  (and (contains? app :name)
       (contains? app :description)))


(defn- valid-tok?
  "Checks if given application/token pair is valid, as per filedump rules"
  [path tok]
  (if (and (contains? tok :name)
           (contains? tok :description)
           (contains? tok :values)
           (contains? tok :path)
           (token-in-path? (:path tok) path))
    (every? (partial valid-env? path) (:values tok))
    false))

(defn- valid-app-toklist?
  "Runs check for every app/token pair for the tokens associated with an app"
  [app-toklist-pair]
  (let [app     (:app app-toklist-pair)
        toklist (:toklist app-toklist-pair)]
    (every? (partial valid-tok? (:name app)) toklist)))

(defn valid-filedump?
  "Checks if given filedump is valid for reconstructing tokens.

A dump is valid if
- The number of apps equals the number of token app groups.
- The number of apps is not zero
- Each app has name=>NAME_FIELD
- Each app has description=>DESC_FIELD
- (weakly) Each app has nothing beyond the above
- Each token has name=>NAME_FIELD
- Each token has description=>DESC_FIELD
- Each token has values=>VALUES_MAP
- The values map must consist of environment keys.
- Each env key has a map, of value=>VALUE_FIELD and source=>SOURCE_FIELD
- (weakly) Each token has nothing beyond the above
- For each pair of apps and tokens, the app must inherit the token's path
- For each token, among its environments, the source matches the path"
  [filedump]
  (let [apps (:apps filedump)
        tokens (:tokens filedump)]
    (and (pos? (count apps))
         (= (count tokens) (count apps))
         (every? valid-app? apps)
         (every? valid-app-toklist?
                 (map (fn [a b] {:app a :toklist b}) apps tokens)))))

(defn- get-exporter
  "Gets the export function from clojure datastructs to formatted string"
  [format & args]
  (format
   {:json js/generate-string
    :csv #(throw (Exception. "Unimplemented"))}))
;; (fn [s] (apply csv/parse-csv s args))

(defn- get-importer
  "Gets the import function from formatted string to clojure datastructs"
  [format & args]
  (format
   {:json (fn [s] (js/parse-string s true))
    :csv #(throw (Exception. "Unimplemented"))}));; TODO add csv component
;; (fn [s] (apply csv/write-csv s args))



(defn import-tokens
  "Imports the token from the library, appending them to the existing zoo. If a
collision exists, the newer is written. Returns :success or :failed, with
:message if failed."
  [filename format & args]
  (try
    (let [filedump (load-tokens (get-importer format args) filename)]
      (if (valid-filedump? filedump)
        (let [results (plant-tokens! filedump)]
          (if (every? identity results)
            {:status :success}
            {:status :sys-error :message "Token did not install"}))
        {:status :user-error :message "Invalid token file"}))
    (catch IllegalStateException e
      {:status :sys-error :message (.getMessage e)})
    (catch FileNotFoundException e
      {:status :user-error :message (str "Could not find " (.getMessage e))})
    (catch Exception e
      {:status :sys-error :message (.getMessage e)})))

(defn export-tokens
  "Exports the tokens from zookeeper, and dumps them into an export file.
Returns :success or :failed, with :message if failed."
  [filename format & args]
  (try
    (store-tokens! (get-exporter format args) filename (dig-tokens))
    {:status :success}
    (catch Exception e
      {:status :sys-error :message (.getMessage e)})))
