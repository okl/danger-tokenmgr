(ns com.okl.tokenmgr.cli-test
  (:require [com.okl.tokenmgr.cli :refer :all]
            [clojure.test :refer :all]))

(deftest t-arg->map
  (is (= {"FOO" "BAR"} (arg->map "FOO=BAR")))
  (is (= {"FOOBAR" ""} (arg->map "FOOBAR")))
  (is (= {"FOO" "BAR=BAZ"} (arg->map "FOO=BAR=BAZ"))))

