(ns com.okl.tokenmgr.handler
  (:use compojure.core
        com.okl.tokenmgr.tokens
        com.okl.tokenmgr.views
        [ring.util.codec :only (url-decode)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]))
(use '[clojure.string :only (join)])

(defn gen-page [app]
  (page app (get-apps app) (get-tokens app)))

(defroutes app-routes
  (GET "/" []
    (gen-page ""))
  (GET "/application" {}
    (gen-page ""))
  (DELETE "/application/:app" [app]
    (.. System out (println (str "attempting to delete " app)))
    (if (delete-app (url-decode app))
      {:status 200
       :body {:status "success"}}
      {:status 200
       :body {:status "failure" :message "No item to delete"}}))
  (PUT "/application" [name description path]
    (let [pathname (if (empty? path)
                     name
                     (join "/" [path name]))]
      (try
        (if (create-app pathname description)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Item not created"}})
        (catch IllegalStateException e
          {:status 200
           :body {:status "failure" :message (.getMessage e)}}))))
    (GET "/application/:app" [app]
    (gen-page app))
  (DELETE "/token/:token" [token]
     (if (delete-token token)
      {:status 200
       :body {:status "success"}}
      {:status 200
       :body {:status "failure" :message "No item to delete"}}))
  (PUT "/token" [name description environment value path]
    (let [pathname (if (empty? path)
                     name
                     (join "/" [path name]))]
      (try
        (if (create-token pathname description environment value)
          {:status 200
           :body {:status "success"}}
          {:status 200
           :body {:status "failure" :message "Item not created"}})
        (catch Exception e
          {:status 200
           :body {:status "failure" :message (.getMessage e)}}))))
  (GET "/api/tokens/:app" [app]
    (denormalize-tokens (get-tokens (url-decode app))))
  (GET "/testing/appication" []
    (dynamic-page "/"))
  (GET "/testing/appication/:app" [app]
    (dynamic-page app))
  (route/resources "")
  (route/not-found "Not Found"))


(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-params)))
