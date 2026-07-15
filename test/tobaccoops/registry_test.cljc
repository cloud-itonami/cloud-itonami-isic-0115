(ns tobaccoops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [tobaccoops.registry :as registry]))

(deftest cost-exceeds-threshold-test
  (testing "Cost within threshold"
    (is (false? (registry/cost-exceeds-threshold? 400 500))))

  (testing "Cost at threshold (inclusive boundary, not exceeded)"
    (is (false? (registry/cost-exceeds-threshold? 500 500))))

  (testing "Cost exceeds threshold"
    (is (true? (registry/cost-exceeds-threshold? 600 500)))))

(deftest acreage-non-positive-test
  (testing "Positive acreage is valid"
    (is (false? (registry/acreage-non-positive? 40))))

  (testing "Zero acreage is invalid"
    (is (true? (registry/acreage-non-positive? 0))))

  (testing "Negative acreage is invalid"
    (is (true? (registry/acreage-non-positive? -5)))))

(deftest leaf-grade-unknown-test
  (testing "Recognized grade codes are known"
    (is (false? (registry/leaf-grade-unknown? "premium")))
    (is (false? (registry/leaf-grade-unknown? "grade-a")))
    (is (false? (registry/leaf-grade-unknown? "grade-b")))
    (is (false? (registry/leaf-grade-unknown? "grade-c")))
    (is (false? (registry/leaf-grade-unknown? "below-grade")))
    (is (false? (registry/leaf-grade-unknown? "ungraded"))))

  (testing "An unrecognized grade code is unknown"
    (is (true? (registry/leaf-grade-unknown? "AAA+"))))

  (testing "nil grade is unknown"
    (is (true? (registry/leaf-grade-unknown? nil)))))

(deftest confidence-below-floor-test
  (testing "Confidence above floor"
    (is (false? (registry/confidence-below-floor? 0.9 0.7))))

  (testing "Confidence at floor (inclusive, not below)"
    (is (false? (registry/confidence-below-floor? 0.7 0.7))))

  (testing "Confidence below floor"
    (is (true? (registry/confidence-below-floor? 0.5 0.7)))))
