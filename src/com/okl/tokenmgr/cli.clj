(ns com.okl.tokenmgr.cli
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [com.okl.tokenmgr.tokens :refer :all]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure-csv.core :as csv]))

(defn- expand-line [line token]
  "Returns expanded line with provided token."
  (string/replace line (str "__" (key token) "__") (val token)))

(defn process-line [line tokens]
  "Returns expanded line with all provided tokens."
  (log/trace (str "process-line: " line))
  (let [expanded (reduce expand-line line (seq tokens))]
    (if (= expanded line)
      expanded
      (process-line expanded tokens))))


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


(defn- process-dir [dir tokens]
  "find .tmpl files and replace tokens in them"
  (let [dir-file (io/file dir)]
    (log/trace (str "I found things " (find-tmpl-files dir-file)))
    (if (and (.exists dir-file) (.isDirectory dir-file))
      (doseq [f (find-tmpl-files dir-file)]
        (process-file! f tokens))
      (log/error (str dir " is not a directory")))))

(defn- process-token-values-pass [tokens]
  "Single pass of tokens through process-line."
  (into {} (map #(hash-map % (process-line (get tokens %) tokens))
                (keys tokens))))

(defn process-token-values [tokens]
  "Filter token values that contain tokens."
  (log/trace (str "processing token values for " tokens))
  (let [tokenpass (process-token-values-pass tokens)]
    (if (= tokens tokenpass)
      tokens
      (process-token-values tokenpass))))

(defn arg->map [arg]
  "Turn a string XXX=YYY to map {XXX YYY}"
  (let [pair (string/split arg #"=" 2)]
    (if (= 1 (count pair))
      (hash-map arg "")
      (apply hash-map pair))))

(def help-str
  (str "Arguments: filter app evnt dir -- filters .tmpl files replacing tokens "
       "with values\n\n"
       "           load path_to_csv app -- loads in a new csv into a specified "
       "application\n\n"
       "           export app path_to_csv -- exports the entire repository into"
       "a csv file\n\n"
       "           import app path_to_csv -- loads the entire repository from a"
       " csv file"))

(defn- usage [parsed-opts]
  (if (:errors parsed-opts)
    (println (string/join "/" (:errors parsed-opts))))
  (println (str help-str "\n\nAdditonal parameters\n" (:summary parsed-opts)))
  (System/exit 1))

(def cli-opts
  [["-t" "--token" "Token definitions, TOKEN=VAL"
    :required true
    :parse-fn #(hash-map :token (arg->map %))
    :assoc-fn (fn [previous key val]
                (merge-with coalesce-map previous val))]
   ["-d" "--delimiter" "Character for delimiter in csv-related operations"
    :required true
    :default "\t"
    :valiate [#(= 1 (count %))]]])

(defn- cli-fn [parsed-opts req-arg-cnt]
  (let [my-args (rest (:arguments parsed-opts))]
    (log/debug (str "args are " my-args))
    (if (not (= (count my-args) req-arg-cnt))
      (usage parsed-opts)
      my-args)))

(defn- store-token [app header row]
  (let [token (zipmap header row)
        name (get token "key_name")
        description (get token "description")]
    (log/debug (str "processing " token))
    (doseq [envt (keys token)]
      (log/debug (str "processing token " name " with envt " envt))
      (if (not (or (= envt "key_name")
                   (= envt "description")
                   (= envt "id")
                   (= envt "module")
                   (empty? (get token envt))))
        (create-token
         (string/join "/" [app name])
         description
         envt
         (get token envt))))))

(defn- token->value-list [token environments]
  [(concat [(:name token) (:description token)]
          (map #(let [value (get (get (:values token) %) "value")]
                  (if (nil? value)
                    ""
                    value))
               environments))])

(defn- export-all-apps [filename]
  nil)

(defn- export-single-app [app filename delimiter]
  (let [tokens (get-tokens app)
        environments (set (flatten (map #(keys (:values %)) tokens)))]
    (with-open [wrtr (io/writer filename)]
      (.write wrtr (csv/write-csv [(concat ["key_name" "description"]
                                           environments)]
                                   :delimiter delimiter))
      (doall (map #(.write wrtr
                           (csv/write-csv (token->value-list % environments)
                                          :delimiter delimiter))
                  tokens)))))

(defn- do-filter [parsed-opts]
  (let [[app envt dir] (cli-fn parsed-opts 3)
        cli-tokens (:token (:options parsed-opts))
        tokens (get-token-values app envt cli-tokens)
        tokens (process-token-values tokens)]
    (process-dir dir tokens)))

(defn- import-single-app [parsed-opts]
  (let [[app file] (cli-fn parsed-opts 2)
        file-contents (slurp file)
        csv (csv/parse-csv file-contents
                           :delimiter (first (:delimiter (:options parsed-opts))))
        ; first row is column headers
        header (first csv)
        token-rows (rest csv)]
    (doseq [row token-rows]
      (store-token app header row))))

(defn- do-import [parsed-opts]
  (let [parsed-args (:arguments parsed-opts)]
    (if (= (count parsed-args) 2)
      nil
      (import-single-app parsed-opts))))

(defn- do-export [parsed-opts]
  (let [parsed-args (:arguments parsed-opts)]
    (if (= (count parsed-args) 2)
      (export-all-apps (second parsed-args))
      (export-single-app (second parsed-args) (second (rest parsed-args))
                         (:delimiter (:options parsed-opts))))))

(defn exec [cmd opts]
  "Execute a single command or print usage."
  (let [cmds {"filter" do-filter
              "export" do-export
              "import" do-import}]
    ((get cmds cmd #(usage %)) opts)))

(defn -main  [& args]
  (let [parsed-opts (parse-opts args cli-opts)
        parsed-args (:arguments parsed-opts)]
    (log/debug parsed-opts)
    (if (:errors parsed-opts)
      (usage parsed-opts)
      (exec (first parsed-args) parsed-opts))))
