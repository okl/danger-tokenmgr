(ns com.okl.tokenmgr.filter
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [com.okl.tokenmgr.tokens :refer :all]))

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
        (.write writer (str (process-line line tokens) "\n"))))
    (if (.canExecute file)
      (.setExecutable output-file true false))))

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


(defn process-dir [dir tokens]
  "find .tmpl files and replace tokens in them"
  (let [dir-file (io/file dir)]
    (log/trace (str "I found things " (find-tmpl-files dir-file)))
    (if (and (.exists dir-file) (.isDirectory dir-file))
      (doseq [f (find-tmpl-files dir-file)]
        (process-file! f tokens))
      (log/error (str dir " is not a directory")))))
