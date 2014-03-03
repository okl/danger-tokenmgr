(ns com.okl.tokenmgr.cli
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [com.okl.tokenmgr.tokens :refer :all]
            [clojure.tools.cli :refer [cli]]))

(defn- process-line [line tokens]
  (log/trace (str "Attempting to process line " line))
  (let [replace-token
        (fn [string token]
          (let [pattern (java.util.regex.Pattern/quote (str "__"(name (key token)) "__"))
                pattern (re-pattern pattern)
                matcher (re-matcher pattern string)
                replacement (java.util.regex.Matcher/quoteReplacement (str (val token)))]
            (log/trace (str "pattern is " pattern))
            (log/trace (str "Reduced is " reduced))
            (log/trace (str "Replacement is " replacement))
            (.replaceAll matcher replacement)))
        reduced (reduce replace-token line (seq tokens))]
    (if (= reduced line)
      reduced
      (process-line reduced tokens))))


(defn- process-file! [file tokens]
  (log/trace (str "Attempting to process file " (str file)))
  (let [output-file (io/file (string/replace (.getAbsolutePath file) #"\.tmpl$" ""))]
    (with-open [reader (io/reader file)
                writer (io/writer output-file)]
      (doseq [line (line-seq reader)]
        (.write writer (str (process-line line tokens) "\n"))))))

(defn- find-tmpl-files [dir]
  "find all .tmpl files in this directory and below"
  (log/trace (str "List of files in dir " dir " is " (seq (.listFiles dir))))
  (flatten
   (remove
    nil?
    (map #(if (.isDirectory %)
            (find-tmpl-files %)
            (when (.endsWith (str %) ".tmpl")
              %))
         (.listFiles dir)))))


(defn- process-dir [dir tokens]
  "find .tmpl files and replace tokens in them"
  (let [dir-file (io/file dir)]
    (log/trace (str "I found things " (find-tmpl-files dir-file)))
    (if (and (.exists dir-file) (.isDirectory dir-file))
      (doseq [f (find-tmpl-files dir-file)]
        (process-file! f tokens))
      (log/error (str dir " is not a directory")))))

(defn- process-token-values [tokens]
  (log/trace (str "processing token values for " tokens))
  (if (empty? (filter #(re-find #"__[^_]+__" (get tokens %))
                      (keys tokens)))
    tokens
    (process-token-values
     (mapcat #(hash-map % (process-line (get tokens %)))
             (keys tokens)))))

(defn- arg->map [arg]
  "Turn a string XXX=YYY to map {XXX YYY}"
  (let [var (first (string/split arg #"="))
        value (second (string/split arg #"="))]
    {var value}))

(defn- usage [usage]
  (println usage)
  (System/exit 1))

(def help-str
  (str "This program will take .tmpl filters, and replace the __TOKENS__ with "
       "values from our token store.\n\nThere are 3 requrired arguments: "
       "app-name, environment, and dir"))

(defn -main  [& args]
  (let [[cli-tokens args help]
        (cli args
             help-str
             ["--token" "Token definitions, TOKEN=VAL"
              :parse-fn arg->map
              :assoc-fn (fn [previous key val]
                          (merge previous val))])]
    (if-not (= 3 (count args))
      (usage help)
      (let [app (first args)
            envt (second args)
            dir (second (rest args)) ;; aka third
            tokens (get-token-values app envt cli-tokens)
            tokens (process-token-values tokens)]
        (process-dir dir tokens)))))
