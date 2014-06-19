(ns com.okl.tokenmgr.cli-test
  (:require [com.okl.tokenmgr.cli :refer :all]
            [clojure.test :refer :all]))

(deftest t-arg->map
  (is (= {"FOO" "BAR"} (arg->map "FOO=BAR")))
  (is (= {"FOOBAR" ""} (arg->map "FOOBAR")))
  (is (= {"FOO" "BAR=BAZ"} (arg->map "FOO=BAR=BAZ"))))

(deftest t-process-line
  (is (= "FOO BAR"
         (process-line "FOO __FOO__" {"FOO" "BAR"})))
  (is (= "FOO BAZ"
         (process-line "FOO __FOO__" {"FOO" "__BAR__", "BAR" "BAZ"}))))

(deftest t-process-token-values
  (is (= {"FOO" "BAR"}
         (process-token-values {"FOO" "BAR"})))
  (is (= {"FOO" "BAR", "BAZ" "BAR"}
         (process-token-values {"FOO" "BAR", "BAZ" "__FOO__"}))))
