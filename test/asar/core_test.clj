(ns asar.core-test
  (:require [asar.core :as sut]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.string :as str])
  (:import (java.io File IOException)
           (java.security MessageDigest)))

(defn digest [^bytes data]
  (->> (doto (MessageDigest/getInstance "MD5")
         (.update data))
       .digest
       (map #(format "%02x" %))
       str/join))

(deftest test-asar
  (testing "Load test.asar and check its contents"
    (let [tst (File. "test/test.asar")
          asar (sut/load-asar tst)]

      (is (= #{"foo.txt" "bar.txt" "pics/smile.png" "deep/hierarchy/here/read.me"}
             (set (sut/list-files asar))))


      (is (= "small file\n" (String. (sut/read-file asar "foo.txt") "UTF-8")))
      (is (= "this file is deep inside\n" (String. (sut/read-file asar "deep/hierarchy/here/read.me") "UTF-8")))

      (testing "Binary image file has the correct size and hash"
        (is (= 225 (:size (sut/file-info asar "pics/smile.png"))))
        (is (= "6e2e560055b8460fc74e14f33aa87c34"
               (digest (sut/read-file asar "pics/smile.png"))))))))

(deftest test-auto-close
  (let [asar (sut/load-asar (File. "test/test.asar"))]
    (with-open [a asar]
      (is (= "small file\n" (String. (sut/read-file a "foo.txt") "UTF-8"))))

    (testing "Read after close should throw"
      (is (thrown? IOException
                   (sut/read-file asar "foo.txt"))))))
