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




(defn- gen-dynamic-header [title]
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

(defn dynamic-page [app]
  (html5
   (gen-dynamic-header (str my-name ":" app))
   [:body
    [:h1 (generate-breadcrumbs app)]
    [:div {:id "testdiv" :style "width:80%;height:300px;"} ""]
    [:script {:type "text/javascript"}
     (str "
var grid;
var dv;

function requiredFieldValidator(value) {
  if (value == null || value == undefined || !value.length) {
      return {valid: false, msg: 'This is a required field'};
    } else {
      return {valid: true, msg: null};
    }
  }

var columns = [
  {id: 'token', name: 'Token', field: 'name', width: 100, editor: Slick.Editors.Text, validator: requiredFieldValidator, sortable: true},
  {id: 'description', name: 'Description', field: 'description', width: 200, editor: Slick.Editors.Text},
  {id: 'environment', name: 'Environment', field: 'environment', width: 200, sortable: true, editor: Slick.Editors.Text},
  {id: 'value', name: 'Value', field: 'value', width: 200, editor: Slick.Editors.Text},
  {id: 'source', name: 'Source', field: 'source', width: 400, sortable: true},
  {id: 'delete', name: 'Delete', field: 'delete', width: 200, formatter: deleteButtonFormatter}];


function deleteButtonFormatter(row, cell, value, columnDef, dataContext) {
  var button = \"<input class='del' type='button' id='\" + dataContext.id + \"' value='delete'>\";
  return button;
}

  var options = {
    editable: true,
    enableAddRow: false,
    enableCellNavigation: true,
    autoEdit: false,
    autoHeight: true
   };

var loadingIndicator = null;

$(function () {

dv = Slick.Data.DataView();
var gridSorter = function(columnField, isAsc, grid, gridData) {
    var sign = isAsc ? 1 : -1;
    var field = columnField
    gridData.sort(function (dataRow1, dataRow2) {
           var value1 = dataRow1[field], value2 = dataRow2[field];
           var result = (value1 == value2) ?  0 :
                      ((value1 > value2 ? 1 : -1)) * sign;
           return result;
    });
    grid.invalidate();
    grid.render();
}

grid = new Slick.Grid('#testdiv', dv, columns, options);

gridSorter('token', true, grid, dv);

grid.setSortColumn('token', true);

grid.autosizeColumns();

grid.onAddNewRow.subscribe(function (e, args) {
  var item = args.item;
  grid.invalidateRow(dv.length);
  dv.addItem(item);
  grid.updateRowCount();
  grid.render();
});

$.ajax({
  url: '/api/tokens/" (url-encode app) "',
  type: 'GET',
  success: function(data) {
    for (i = 0; i < data.length; i++) {
      data[i]['id'] = data[i]['name'] + ':' + data[i]['environment'];
    }
    dv.setItems(data);
  }});

grid.onSort.subscribe(function(e, args) {
  gridSorter(args.sortCol.field, args.sortAsc, grid, dv);
});

dv.onRowCountChanged.subscribe(function(e, args) {
  grid.updateRowCount();
  grid.render();
});

dv.onRowsChanged.subscribe(function (e, args) {
  grid.invalidateRows(args.rows);
  grid.render();
});

grid.onCellChange.subscribe(function (e, args) {
  var row = dv.getItem(args.row);
  var columns = grid.getColumns();
  var data = {};
  // need to skip the delete column on the right
  for (i = 0; i < columns.length; i++) {
    console.log(i);
    if(columns[i]['editor']) {
       property = columns[i]['field'];
       data[property] = row[property];
    }
  }

  data['path'] = '" app "';

  $.ajax({
  url: '/token',
  type: 'PUT',
  contentType: 'application/json; charset=UTF-8',
  dataType: 'json',
  data: JSON.stringify(data),
  success: function(data) {
    if(data.status != 'success') {
      alert('Error adding token:\\n' + data.message);
    }
    document.location.reload(true);
  },
  error: function(request, status, message) {
    alert('Error adding token ' + data['name']);
    document.location.reload(true);
  }
  });
});
})"
)]]))
