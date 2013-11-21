(ns com.okl.tokenmgr.views
  (:use [hiccup core page]
        [clojure.string :only (join split)]
        [ring.util.codec :only (url-encode url-decode)]
        [clojure.tools.logging :as log]))

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

(defn- gen-header [title]
  [:head
   [:title title]
   [:script {:src
             "//ajax.googleapis.com/ajax/libs/jquery/1.4.3/jquery.min.js"}]
   [:script {:src "http://malsup.github.com/jquery.form.js"}]
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



(defn- delete-link [path]
  [:a
   {:onclick
    (str "var delconf = confirm ('Do you really want to delete " (url-decode path)" ?');
if (delconf == true) {
$.ajax({
url: '/application/" (url-encode path) "',
type: 'DELETE',
success: function(data) {
  if(data.status != 'success') {
    alert('Error deleting " path ":\\n' + data.message)
  }
  document.location.reload(true);
},
error: function(req, status, message) {
  alert('Error deleting " path "');
  document.location.reload(true);
}
});} return false;")
   :href "javascriptIsDisabled"}
   "delete"])

(defn add-link [type]
  [:a
   {:onclick
    (str "$.ajax({
url: '/" type "',
type: 'PUT',
contentType: 'application/json; charset=UTF-8',
dataType: 'json',
data: JSON.stringify($('#" type "form').serializeObject()),
success: function(data) {
  if(data.status != 'success') {
    alert('Error adding " type ":\\n' + data.message);
  }
  document.location.reload(true);
},
error: function(request, status, message) {
  alert('Error adding " type "');
  document.location.reload(true);
}
}); return false;")
    :href "javascriptIsDisabled"}
   "add"])

(defn- apps-div [current-path apps]
  [:div {:name "apps-div"}
   [:h2 "Applications"]
   [:table
    [:tr
     [:th "App"]
     [:th "Description"]
     [:th "Delete"]]
    ;; generate table row per pair
    (map #(let [app %
                name (:name app)
                desc (:description app)
                display-name (if (empty? current-path)
                               name
                               (subs name (inc (count current-path))))]
            [:tr
             [:td (make-link (str "/application/" (url-encode name)) display-name)]
             [:td desc]
             [:td (delete-link name)]])
         (sort #(compare (:name %1) (:name %2)) apps))]
   [:form {:method "POST" :action "/application" :id "applicationform"}
    [:input {:type "hidden" :name "path" :value current-path}]
    [:table
     [:tr
      [:td "App:"]
      [:td
       [:input {:type "text" :name "name"}]]]
     [:tr [:td "Description:"]
      [:td
       [:input {:type "text" :name "description"}]]]
     [:tr
      [:td {:colspan "2" :align "right"}
       (add-link "application")]]]]])

(defn- generate-token-row [path token]
  (let [name (:name token)
        desc (:description token)
        values (:values token)
        token-path (:path token)
        pathname (if (empty? token-path)
                   name
                   (join "/" [token-path name]))
        num-envts (count values)
        first-key (first (keys values))
        other-keys (rest (keys values))]
    (concat
     [[:tr
       [:td {:rowspan num-envts} name]
       [:td {:rowspan num-envts} desc]
       [:td first-key]
       [:td (get (get values first-key) "value")]
       [:td (path->pretty-path (get (get values first-key) "source"))]
       (if (= token-path path)
         [:td {:rowspan num-envts} (delete-link (url-encode pathname))])
       ]]
     (map #(vector :tr
                   [:td %]
                   [:td (get (get values %) "value")]
                   [:td (path->pretty-path (get (get values %) "source"))])
          other-keys))))


(defn- tokens-div [app tokens]
  [:div {:name "tokens-div"}
   [:h2 "Tokens"]
   [:table {:border 1}
    [:tr
     [:th "Token"]
     [:th "Description"]
     [:th "Envt"]
     [:th "Value"]
     [:th "Source"]
     [:th "Delete"]]
    (map #(generate-token-row app %)
         (sort #(compare (:name %1) (:name %2)) tokens))]
    ;; generate table row per pair
   [:form {:method "POST" :action "/" :id "tokenform"}
    [:input {:type "hidden" :name "path" :value app}]
    [:table
     [:tr
      [:td "Token:"]
      [:td
       [:input {:type "text" :name "name"}]]]
     [:tr [:td "Description:"]
      [:td
       [:input {:type "text" :name "description"}]]]
     [:tr [:td "Environment"]
      [:td
       [:input {:type "text" :name "environment"}]]]
     [:tr [:td "Value"]
      [:td
       [:input {:type "text" :name "value"}]]]
     [:tr
      [:td {:colspan "2" :align "right"}
       (add-link "token")]]]]])

(def my-name "Zookeeper Tokens")

(defn page [app apps tokens]
  (html5
   (gen-header (str my-name ":" app))
     [:body
      [:h1 (generate-breadcrumbs app)]
      (apps-div app apps)
      (tokens-div app tokens)]))
