(ns com.okl.tokenmgr.handler
  (:import
   (java.io FileInputStream FileNotFoundException IOException File))
  (:use compojure.core
        com.okl.tokenmgr.tokens
        com.okl.tokenmgr.views
        [ring.util.codec :only (url-decode)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.middleware.multipart-params :as mp]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [roxxi.utils.print :refer :all]
            [com.okl.tokenmgr.config :as config]
            [com.okl.tokenmgr.port :as port]
            [clojure.string :as str]))
(use '[clojure.string :only (join)])

(defn- delimiter []
  (let [broker (config/make-yaml-config-broker "conf/tokenmgr.yml")
        config (.web-configuration broker)]
    (:delimiter config)))

(defn- unpack-status [status]
  (case (:status status)
    :success    {:status 200
                 :body "Success"}
    :user-error {:status 400
                 :body (str "Warning: " (:message status))}
    :sys-error  {:status 500
                 :body (str "Error: " (:message status))}))

(defroutes app-routes
  (GET "/" []
    (page ""))
  (GET "/application" {}
    (page ""))
  (GET "/application/:app" [app]
    (page app))
  (GET "/api/applications/" []
    (get-apps ""))
  (GET "/api/applications/:app" [app]
    (get-apps app))
  (GET "/api/tokens/:app" [app sort-col sort-dir]
    (let [tokens (denormalize-tokens (get-tokens (url-decode app)))
          sort-dir (or sort-dir "asc")
          sort-col (or sort-col "name")
          sorted-data (sort-by #(get % (keyword sort-col)) tokens)]
      (if (= "asc" sort-dir)
        sorted-data
        (reverse sorted-data))))
  (GET "/api/tokens/" [sort-dir sort-col]
    (let [tokens (denormalize-tokens (get-tokens ""))]
      (println "Sorting by " sort-col)
      (sort-by (symbol sort-col) tokens)))
  (DELETE "/api/applications/:delimited-app" [delimited-app]
    (try
      (let [app (str/replace delimited-app (delimiter) "/")]
        (if (delete-app app)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Item not deleted"}}))
      (catch IllegalStateException e
        {:status 200
         :body {:status "failure" :message (.getMessage e)}})))
  (PUT "/api/applications" {body :body}
    (try
      (let [apps (json/read-str (slurp body))
            results (map (fn[app]
                           (let [path (get app "path")
                                 name (get app "name")
                                 pathname (if (empty? path)
                                            name
                                            (join "/" [path name]))]
                             (create-app pathname (get app "description"))))
                         apps)]
        (if (every? identity results)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Some items may not have been updated"}}))
      (catch IllegalStateException e
        {:status 200
         :body {:status "failure" :message (.getMessage e)}})))
  (DELETE "/api/tokens/:delimited-pathname/:envt" [delimited-pathname envt]
      (try
        (let [pathname (str/replace delimited-pathname (delimiter) envt)]
          (if (delete-value pathname envt)
            {:status 200
             :body {:status "success"}}
            {:status 200
             :body {:status "failure" :message "Item not created"}}))
        (catch IllegalStateException e
          {:status 200
           :body {:status "failure" :message (.getMessage e)}})))
  (PUT "/api/tokens" {body :body}
    (try
      (let [tokens (json/read-str (slurp body))
            results (map (fn[token]
                           (let [path (get token "path")
                                 name (get token "name")
                                 pathname (if (empty? path)
                                            name
                                            (join "/" [path name]))]
                             (create-token pathname
                                           (get token "description")
                                           (get token "environment")
                                           (get token "value"))))
                         tokens)]
        (if (every? identity results)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Some items may not have been updated"}}))
      (catch IllegalStateException e
        {:status 200
         :body {:status "failure" :message (.getMessage e)}})))

;;; For debug, useful for file import/export from website,
;;; but needs to accept web file transfer.

  ;; (POST "/api/file/import" []
  ;;   (let [status (port/import-tokens "store.json" :json)]
  ;;     (unpack-status status)))

  ;; (POST "/api/file/export" []
  ;;   (let [status (port/export-tokens "store.json" :json)]
  ;;     (unpack-status status)))

  (route/resources "")
  (route/not-found "Not Found"))


(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-params)))
