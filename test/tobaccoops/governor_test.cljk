(ns tobaccoops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [tobaccoops.governor :as gov]
            [tobaccoops.store :as store]))

(deftest hard-violations-no-field-id
  (testing "Hard violation: missing field-id"
    (let [req {}
          prop {:op :log-cultivation-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (seq (:violations verdict)))
      (is (some #(= :field-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-unregistered-field
  (testing "Hard violation: field-id present but not registered"
    (let [req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :field-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-not-propose
  (testing "Hard violation: effect is not :propose"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :execute}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-execution (:rule %)) (:violations verdict))))))

(deftest hard-violations-equipment-blocked
  (testing "Hard violation: direct field-equipment operation is permanently blocked"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :operate-field-equipment :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-curing-decision-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-curing-barn-temperature-decision-blocked
  (testing "Hard violation: finalizing a curing-barn temperature decision is permanently blocked"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :finalize-curing-barn-temperature-decision :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-curing-decision-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-pesticide-decision-blocked
  (testing "Hard violation: finalizing a pesticide-application decision is permanently blocked"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :finalize-pesticide-application :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-curing-decision-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-op-not-allowed
  (testing "Hard violation: op outside the closed allowlist"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :dispatch-drone-defoliant :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

(deftest hard-violations-cultivation-record-invalid
  (testing "Hard violation: non-positive acreage"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :propose :acreage 0 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :cultivation-record-invalid (:rule %)) (:violations verdict))))))

(deftest hard-violations-leaf-grade-invalid
  (testing "Hard violation: unrecognized leaf-quality grade code"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :propose :acreage 40
                :leaf-grade "AAA+" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :leaf-grade-invalid (:rule %)) (:violations verdict))))))

(deftest ok-cultivation-logging
  (testing "OK: valid cultivation record logging with a registered field"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :propose :acreage 40 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest ok-cultivation-logging-with-recognized-leaf-grade
  (testing "OK: valid cultivation record logging with a recognized leaf-grade code"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :propose :acreage 40
                :leaf-grade "grade-a" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest escalation-crop-health-concern
  (testing "Escalation: crop pest (e.g. tobacco hornworm)/disease (e.g. blue mold)/curing-defect concern ALWAYS escalates, even at high confidence"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :flag-crop-health-concern :effect :propose
                :concern "タバコスズメガ（tobacco hornworm）の可能性" :confidence 0.95}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict)))))

(deftest escalation-low-confidence
  (testing "Escalation: confidence below the floor"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :log-cultivation-record :effect :propose :acreage 40 :confidence 0.5}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-high-cost
  (testing "Escalation: supply order over the (default) cost threshold"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :order-supplies :effect :propose :cost 1000 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-category-specific-threshold
  (testing "Escalation: supply order over its category-specific threshold (curing-fuel: 1000)"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :order-supplies :effect :propose :cost 1200 :confidence 0.9
                :value {:category "curing-fuel"}}
          verdict (gov/check req nil prop s)]
      (is (:escalate? verdict))))

  (testing "OK: curing-fuel order under its higher category threshold"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :order-supplies :effect :propose :cost 800 :confidence 0.9
                :value {:category "curing-fuel"}}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-supply-order-low-cost
  (testing "OK: supply order under the cost threshold"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :order-supplies :effect :propose :cost 100 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-schedule-field-operation
  (testing "OK: scheduling a field/curing operation is a routine coordination op"
    (let [field {:id "field-001" :name "Test Farm North Field"}
          s (store/mem-store {:initial-fields {"field-001" field}})
          req {:field-id "field-001"}
          prop {:op :schedule-field-operation :effect :propose :confidence 0.85}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))
