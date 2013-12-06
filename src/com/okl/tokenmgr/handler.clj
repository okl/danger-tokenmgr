(ns com.okl.tokenmgr.handler
  (:use compojure.core
        com.okl.tokenmgr.tokens
        com.okl.tokenmgr.views
        [ring.util.codec :only (url-decode)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [roxxi.utils.print :refer :all]))
(use '[clojure.string :only (join)])

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
  (GET "/api/tokens/:app" [app]
    (denormalize-tokens (get-tokens (url-decode app))))
  (GET "/api/tokens/" []
    (denormalize-tokens (get-tokens "")))
  (DELETE "/api/applications/:app" [app]
    (try
      (if (delete-app app)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Item not deleted"}})
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
  (DELETE "/api/tokens/:pathname/:envt" [pathname envt]
      (try
        (if (delete-value pathname envt)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Item not created"}})
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
  (route/resources "")
  (route/not-found "Not Found"))


(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-params)))
