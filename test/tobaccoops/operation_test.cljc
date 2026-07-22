(ns tobaccoops.operation-test
  "Integration tests for `tobaccoops.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. This namespace did not exist before the
  deferred-stub fix: `operation/build` returned `(fn invoke-operation
  [request context] (run-operation store request context opts))`, a
  plain synchronous threading pipeline that never touched
  `kotoba-lang/langgraph` at all. These tests prove the compiled graph is
  real and that the audit ledger (`tobaccoops.store/append-ledger!`) is
  genuinely wired into the `:commit`/`:hold`/`:request-approval` nodes --
  falsifiable on real StateGraph behavior, not hardcoded pass strings:
  hold-until-approved, ledger stays empty until commit, governor
  rejection blocks commit. Mirrors `transportops.operation-test`
  (cloud-itonami-isic-869) / would mirror an equivalent for
  cloud-itonami-isic-0111 (cerealops), which does not yet have one."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [tobaccoops.operation :as operation]
            [tobaccoops.store :as store]))

(def farmer {:actor-id "tobacco-ops-01" :role :farm-operator :phase :phase-3})

(defn- exec [actor tid request]
  (g/run* actor {:request request :context farmer} {:thread-id tid}))

(defn- seeded-store []
  (store/mem-store
   {:initial-fields
    {"field-001" {:id "field-001" :name "Test Farm North Field"
                  :tobacco-type "burley"}}}))

(deftest commit-path-clean-proposal
  (testing "a clean, phase-3, high-confidence cultivation-record request
            commits through the real compiled graph and appends exactly
            one fact to the audit ledger"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:op :log-cultivation-record :field-id "field-001"
                        :acreage 40 :leaf-grade "grade-a"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :log-cultivation-record (:op (first ledger))))
        (is (= "field-001" (:subject (first ledger))))))))

(deftest hard-hold-path-unregistered-field
  (testing "an unregistered field is a HARD, permanent governor violation
            -- the real graph routes straight to :hold (no interrupt, no
            human-approval detour) and durably records the hold fact,
            and the ledger stays empty of any :committed fact"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-hold"
                       {:op :log-cultivation-record :field-id "field-999"
                        :acreage 40 :leaf-grade "grade-a"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :field-not-registered (:rule %)) (:violations (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "governor rejection blocks commit -- no :committed fact ever lands")))))

(deftest escalate-then-approve-commits
  (testing ":flag-crop-health-concern ALWAYS escalates -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval; the
            ledger stays completely empty until a human farmer/agronomist
            approve! resumes the SAME compiled graph and commits via the
            graph's own :request-approval -> :commit edge"
    (let [s (seeded-store)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:op :flag-crop-health-concern :field-id "field-001"
                      :concern "疫病の可能性"})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s))
          "hold-until-approved: not yet committed -- awaiting human sign-off, ledger stays empty until commit")
      (let [approved (g/run* actor {:approval {:status :approved :by "farmer-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :flag-crop-health-concern (:op (first ledger))))
          (is (= "farmer-01" (get-in approved-state [:record :payload :approved-by]))))))))

(deftest escalate-then-reject-holds
  (testing "a human farmer/agronomist rejecting an escalated request
            routes to :hold via the :request-approval node's own
            decision (governor rejection / human rejection both block
            commit), and durably records the rejection -- not a
            hand-rolled parallel path"
    (let [s (seeded-store)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :flag-crop-health-concern :field-id "field-001"
                       :concern "カビの可能性"})
          rejected (g/run* actor {:approval {:status :rejected :by "farmer-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "a rejected approval never reaches :commit")))))

(deftest phase-0-forces-escalation-even-when-governor-clean
  (testing "phase-0 (simulation) forces EVERY otherwise-clean commit
            through human review -- the phase gate independently
            overrides an otherwise-:commit governor verdict, proven
            against the real compiled graph. Compares two independent
            stores: a phase-3 context commits, the SAME clean proposal
            under a phase-0 context only interrupts, ledger stays empty."
    (let [request {:op :log-cultivation-record :field-id "field-001"
                   :acreage 40 :leaf-grade "grade-a"}
          s3 (seeded-store)
          actor3 (operation/build s3)
          result (exec actor3 "t-phase3" request)

          s0 (seeded-store)
          actor0 (operation/build s0)
          held (g/run* actor0 {:request request
                               :context (assoc farmer :phase :phase-0)}
                       {:thread-id "t-phase0"})]
      ;; the phase-3 farmer context commits (sanity check, mirrors
      ;; commit-path-clean-proposal above)
      (is (= :commit (:disposition (:state result))))
      (is (seq (store/ledger s3)))
      ;; the SAME proposal under a phase-0 context only interrupts --
      ;; no autonomous commit, ledger stays empty until a human resumes
      (is (= :interrupted (:status held)))
      (is (empty? (store/ledger s0))))))
