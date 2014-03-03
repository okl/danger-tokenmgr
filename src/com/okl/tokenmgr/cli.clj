(ns com.okl.tokenmgr.cli
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [com.okl.tokenmgr.tokens :refer :all]
            [clojure.tools.cli :refer [parse-opts]]))

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

(def help-str
  (str "Arguments: filter app evnt dir -- filters .tmpl files replacing tokens "
       "with values\n\n"
       "           load path_to_csv app -- loads in a new csv into a specified "
       "application\n\n"
       "           export path_to_csv -- exports the entire repository into a "
       "csv file\n\n"
       "           import path_to_csv -- loads the entire repository from a "
       "csv file"))

(defn- usage [info]
  (println (str help-str "\n\nAdditonal parameters\n" info))
  (System/exit 1))

(def cli-opts
  [["-t" "--token" "Token definitions, TOKEN=VAL"
    :parse-fn arg->map
    :assoc-fn (fn [previous key val]
                (merge previous val))]
   ["-d" "--delimiter" "Character for delimiter in csv-related operations"
    :default \tab
    :valiate [#(= 1 (count %))]]])

(defn- do-filter [parsed-opts]
  (let [args (:arguments parsed-opts)]
    (log/info (str "found " (count args) " args: " args))
    (if (not (= (count args) 4))
      (usage (:summary parsed-opts)))
    (let [[app envt dir] (rest args) ; skipping the filter argument
          cli-tokens (:token (:options parsed-opts))
          tokens (get-token-values app envt cli-tokens)
          tokens (process-token-values tokens)]
      (process-dir dir tokens))))

(defn- do-import [parsed-opts]
  nil)

(defn- do-export [parsed-opts]
  nil)

(defn- do-load [parsed-opts]
  nil)

(defn -main  [& args]
  (let [parsed-opts (parse-opts args cli-opts)
        parsed-args (:arguments parsed-opts)]
    (if (:errors parsed-opts)
      (usage (:summary parsed-opts)))
    (cond
     (= (first parsed-args) "filter") (do-filter parsed-opts)
     (= (first parsed-args) "load") (do-load parsed-opts)
     (= (first parsed-args) "export") (do-export parsed-opts)
     (= (first parsed-args) "import") (do-import parsed-opts)
     :else (usage (:summary parsed-opts)))))
