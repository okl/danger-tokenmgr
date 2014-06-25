(ns com.okl.tokenmgr.cli-test
  (:require [com.okl.tokenmgr.cli :refer :all]
            [clojure.test :refer :all]))

(deftest t-arg->map
  (is (= {"FOO" "BAR"} (arg->map "FOO=BAR")))
  (is (= {"FOOBAR" ""} (arg->map "FOOBAR")))
  (is (= {"FOO" "BAR=BAZ"} (arg->map "FOO=BAR=BAZ"))))

(deftest t-expand-line
  (is (= "FOO BAR"
         (expand-line "FOO __FOO__" {"FOO" "BAR"})))
  (is (= "BAZ" (expand-line "__FOO_BAR__" {"FOO_BAR" "BAZ"})))
  (is (= "BAZ" (expand-line "__FOO2__" {"FOO2" "BAZ"})))
  (is (= "_BAZ_" (expand-line "___FOO___" {"FOO" "BAZ"})))
  (is (= "BAZ_" (expand-line "__FOO___" {"FOO" "BAZ"})))
  (is (= "_BAZ" (expand-line "___FOO__" {"FOO" "BAZ"})))
  (is (= "___FOO___" (expand-line "___FOO___" {}))))

(deftest t-process-token-values
  (is (= {"FOO" "BAR"}
         (process-token-values {"FOO" "BAR"})))
  (is (= {"FOO" "BAR", "BAZ" "BAR"}
         (process-token-values {"FOO" "BAR", "BAZ" "__FOO__"})))
  (is (= {"FOO" "BAZ", "BAR_BAZ" "BAZ"}
         (process-token-values {"FOO" "__BAR_BAZ__", "BAR_BAZ" "BAZ"})))
  (is (= {"FOO" "__BAR__BAZ__", "BAR_BAZ" "BAZ"}
         (process-token-values {"FOO" "__BAR__BAZ__", "BAR_BAZ" "BAZ"}))))
