(ns com.okl.tokenmgr.views
  (:use [hiccup core page]
        [clojure.string :only (join split)]
        [ring.util.codec :only (url-encode url-decode)]
        [clojure.tools.logging :as log])
  (:require [clojure.data.json :as json]))

(defn- path->pretty-path [path]
  (str "/" path))

(defn- make-link [path name]
  [:a {:href path} name])

(defn- path->url-encoded-path [path]
  (if (.startsWith path "/application/")
    (str "/application/" (url-encode (subs path (count "/application/"))))
    (url-encode path)))

(defn- generate-breadcrumbs [path]
  "Generate a url breadcrumb string at the top of the page"
  (if (empty? path)
    [:a {:href "/application"} "/" ]
    (let [crumbs (split path #"/")]
      (reduce
       (fn [result next]
         (let [last-path (:href (get (last result) 1))]
           (concat result
                   [[:a {:href
                         (path->url-encoded-path
                          (str last-path "/" next))} (str next "/")]])))
       [[:a {:href "/application"} "/" ]]
       crumbs))))

(def my-name "Zookeeper Tokens")

(defn- gen-header [title]
  [:head
   [:title title]
   [:script {:src
             "//ajax.googleapis.com/ajax/libs/jquery/1.4.3/jquery.min.js"}]
   [:script {:src "http://malsup.github.com/jquery.form.js"}]
   [:link {:rel "stylesheet"
           :href "/slick-grid/slick.grid.css"
           :type "text/css"}]
   [:link {:rel "stylesheet"
           :href "/slick-grid/css/smoothness/jquery-ui-1.8.16.custom.css"
           :type "text/css"}]
   [:script {:src "/jquery.event.drag-2.0.min.js"}]
   [:script {:src "/slick-grid/slick.core.js"}]
   [:script {:src "/slick-grid/slick.grid.js"}]
   [:script {:src "/slick-grid/slick.editors.js"}]
   [:script {:src "/slick-grid/plugins/slick.cellrangedecorator.js"}]
   [:script {:src "/slick-grid/plugins/slick.cellrangeselector.js"}]
   [:script {:src "/slick-grid/plugins/slick.rowselectionmodel.js"}]
   [:script {:src "/slick-grid/slick.dataview.js"}]
   [:script {:src "//code.jquery.com/ui/1.10.3/jquery-ui.js"}]
   [:script {:src "/token.slickgrid.js"}]
   [:script {:src "/application.slickgrid.js"}]
   [:script {:type "text/javascript"}
    "$.fn.serializeObject = function()
{
    var o = {};
    var a = this.serializeArray();
    $.each(a, function() {
        if (o[this.name] !== undefined) {
            if (!o[this.name].push) {
                o[this.name] = [o[this.name]];
            }
            o[this.name].push(this.value || '');
        } else {
            o[this.name] = this.value || '';
        }
    });
    return o;
};"]])

(defn page [app]
  (let [url-encoded-app (if (empty? app)
                         ""
                         (url-encode app))]
    (html5
     (gen-header (str my-name ":" app))
     [:body
      [:h1 (generate-breadcrumbs app)]
      [:div {:id "appdiv" :style "width:80%;height:300px;"} ""]
      [:input {:type "submit" :class "AppAddNewRow" :value "Add New Row"}]
      [:input {:type "submit" :class "AppSubmitChanges" :value "Submit Changes"}]
      [:input {:type "submit" :class "AppResetChanges" :value "Reset Changes"}]
      [:script {:type "text/javascript"}
       (str "new AppSlickGrid('" app "', '"url-encoded-app "');")]
      [:div {:id "tokendiv" :style "width:80%;height:300px;"} ""]
      [:input {:type "submit" :class "TokenAddNewRow" :value "Add New Row"}]
      [:input {:type "submit" :class "TokenSubmitChanges" :value "Submit Changes"}]
      [:input {:type "submit" :class "TokenResetChanges" :value "Reset Changes"}]
      [:script {:type "text/javascript"}
       (str "new TokenSlickGrid('" app "', '"url-encoded-app "');")]])))
