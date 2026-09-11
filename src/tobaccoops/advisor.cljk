(ns tobaccoops.advisor
  "TobaccoOpsAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes back-office coordination actions (planting/
  curing/grading batch and leaf-quality record logging, planting/
  harvesting/curing scheduling, crop-health/curing-defect concern flags,
  supply procurement) based on field state and operator input. The
  advisor is SEALED into the `:advise` step of the operation flow; every
  proposal is routed through the independent Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (field registration, closed-op allowlist,
       cost/crop-health gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `fibreops.advisor`, cloud-itonami-isic-0116)."
  )

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g.
    :acreage/:leaf-grade/:cost)."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op field-id]} request]
      (case op
        :log-cultivation-record
        {:op :log-cultivation-record
         :effect :propose
         :acreage (:acreage request 0)
         :leaf-grade (:leaf-grade request "ungraded")
         :value {:field-id field-id
                 :acreage (:acreage request 0)
                 :tobacco-type (:tobacco-type request "unspecified")
                 :leaf-grade (:leaf-grade request "ungraded")
                 :record-type (:record-type request "planting")}
         :cites ["operator-submitted-field-data"]
         :summary "Planting/curing/grading batch and leaf-quality record entry logged from operator submission"
         :confidence 0.9}

        :schedule-field-operation
        {:op :schedule-field-operation
         :effect :propose
         :value {:field-id field-id
                 :operation-type (:operation-type request "planting")
                 :requested-date (:requested-date request)
                 :reason (:reason request "routine-schedule")}
         :cites ["operator-scheduling-request"]
         :summary "Field/curing operation (planting/topping/harvesting/curing/grading) proposed per operator request"
         :confidence 0.85}

        :flag-crop-health-concern
        {:op :flag-crop-health-concern
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:field-id field-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "agronomist-review"}
         :cites ["operator-observation"]
         :summary "Crop pest (e.g. tobacco hornworm)/disease (e.g. blue mold)/curing-defect (e.g. barn rot) concern flagged for agronomist/farmer review"
         :confidence 0.8}

        :order-supplies
        {:op :order-supplies
         :effect :propose
         :cost (:cost request 0)
         :value {:field-id field-id
                 :category (:category request "fertilizer")
                 :cost (:cost request 0)}
         :cites ["operator-procurement-request"]
         :summary "Supply order (fertilizer/pesticide/curing-fuel) proposed for field"
         :confidence 0.85}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :field-id (:field-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
