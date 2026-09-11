(ns tobaccoops.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [tobaccoops.facts :as facts]))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "fertilizer")]
      (is (= "fertilizer" (:id c)))
      (is (= "肥料" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "fertilizer"   500
      "pesticide"    500
      "curing-fuel"  1000)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest tobacco-type-lookup
  (testing "Lookup valid tobacco type"
    (are [id expected-name] (= expected-name (:name (facts/tobacco-type-by-id id)))
      "flue-cured"    "黄色種（フルーキュアド）"
      "burley"        "バーレー種"
      "oriental"      "東洋種（オリエント）"
      "dark-fired"    "ダークファイアード種"
      "cigar-wrapper" "葉巻用ラッパー種"))

  (testing "Lookup invalid tobacco type"
    (is (nil? (facts/tobacco-type-by-id "unknown"))))

  (testing "Fibre crops are out of scope (ISIC 0116, not this actor)"
    (is (nil? (facts/tobacco-type-by-id "cotton")))))

(deftest field-operation-types-reference-set
  (testing "Tobacco-specific field/curing operation types are present"
    (is (contains? facts/field-operation-types "planting"))
    (is (contains? facts/field-operation-types "topping"))
    (is (contains? facts/field-operation-types "harvesting"))
    (is (contains? facts/field-operation-types "curing"))
    (is (contains? facts/field-operation-types "grading")))

  (testing "Not a validated enum -- an unlisted operation type is simply absent"
    (is (not (contains? facts/field-operation-types "retting")))))

(deftest leaf-quality-grades-closed-set
  (testing "Recognized leaf-quality grade codes are present"
    (is (contains? facts/leaf-quality-grades "premium"))
    (is (contains? facts/leaf-quality-grades "grade-a"))
    (is (contains? facts/leaf-quality-grades "grade-b"))
    (is (contains? facts/leaf-quality-grades "grade-c"))
    (is (contains? facts/leaf-quality-grades "below-grade"))
    (is (contains? facts/leaf-quality-grades "ungraded")))

  (testing "An unrecognized grade code is absent from the closed vocabulary"
    (is (not (contains? facts/leaf-quality-grades "AAA+")))
    (is (not (contains? facts/leaf-quality-grades "")))))
