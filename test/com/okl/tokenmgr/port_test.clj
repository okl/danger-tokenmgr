(ns com.okl.tokenmgr.port-test
  (:require [com.okl.tokenmgr.port :refer :all]
            [clojure.test :refer :all]))


(def desc "Sample description")
(def name-counter (atom 0))
(defn- newname [] (str "name-" (swap! name-counter (partial + 1))))

(defn- new-app-base []
  {:name (newname) :description desc})

(defn- gen-tokens [app n]
  (vec(map (fn [i] {:name (str "name-" i)
                :description desc
                :values {"prod" {:value "value"
                                 :source (:name app)}}
                :path (:name app)})
         (vec (range n)))))

(defn- filedump-base
  [tokcount-vec]
  (let [app-list (vec (map (fn [_] (new-app-base)) tokcount-vec))]
    {:apps app-list
     :tokens (vec(map gen-tokens app-list tokcount-vec))}
    ;;; BUG: does not yet support multiple environments
    ))

(def app-missing-name
  {:apps [{:description desc}]
   :tokens [[]]})

(def app-missing-desc
  {:apps [{:name "name"}]
   :tokens [[]]})

(def token-missing-name
  {:apps [{:description desc}]
   :tokens [[{:description desc
              :values {"prod" {:value "value"
                              :source "name"}}
              :path "name"}]]})

(def token-missing-description
  {:apps [{:name "name"}]
   :tokens [[{:name "othername"
              :values {"prod" {:value "value"
                              :source "name"}}
              :path "name"}]]})

(def token-missing-value
  {:apps [{:name "name"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :values {"prod" {:source "name"}}
              :path "name"}]]})

(def token-missing-values
  {:apps [{:name "name"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :path "name"}]]})

(def token-missing-source
  {:apps [{:name "name"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :values {"prod" {:value "value"}}
              :path "name"}]]})

(def token-missing-path
  {:apps [{:name "name"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :values {"prod" {:value "value"
                              :source "name"}}}]]})

(def token-source-app-inconsistent
  {:apps [{:name "name"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :values {"prod" {:value "value"
                              :source "name"}}
              :path "name2"}]]})

(def token-source-path-inconsistent
  {:apps [{:name "name2"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :values {"prod" {:value "value"
                              :source "name"}}
              :path "name"}]]})

(def token-app-path-inconsistent
  {:apps [{:name "name"
           :description desc}]
   :tokens [[{:name "othername"
              :description desc
              :values {"prod" {:value "value"
                              :source "name2"}}
              :path "name"}]]})

(deftest t-valid-filedump?
  (is (do (not (valid-filedump? {}))))
  (is (do (not (valid-filedump? (filedump-base [])))))
  (is (do (valid-filedump? (filedump-base [0]))))
  (is (do (valid-filedump? (filedump-base [1]))))
  (is (do (valid-filedump? (filedump-base [0 0]))))
  (is (do (valid-filedump? (filedump-base [0 1]))))
  (is (do (valid-filedump? (filedump-base [1 1]))))
  (is (do (valid-filedump? (filedump-base [1 2 3 4 5]))))
  (is (do (not (valid-filedump? app-missing-desc))))
  (is (do (not (valid-filedump? app-missing-name))))
  (is (do (not (valid-filedump? token-missing-name))))
  (is (do (not (valid-filedump? token-missing-description))))
  (is (do (not (valid-filedump? token-missing-value))))
  (is (do (not (valid-filedump? token-missing-values))))
  (is (do (not (valid-filedump? token-missing-source))))
  (is (do (not (valid-filedump? token-missing-path))))
  (is (do (not (valid-filedump? token-source-path-inconsistent))))
  (is (do (not (valid-filedump? token-app-path-inconsistent))))
  (is (do (not (valid-filedump? token-source-app-inconsistent))))
  )
