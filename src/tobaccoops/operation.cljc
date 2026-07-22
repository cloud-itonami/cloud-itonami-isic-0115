(ns tobaccoops.operation
  "OperationActor -- one tobacco-growing operation = one supervised
  actor run, expressed as a langgraph-clj StateGraph. The advisor
  (TobaccoOpsAdvisor) is sealed into a single node (:advise); its
  proposal is ALWAYS routed through the Tobacco Operations Governor
  (:govern) and the rollout phase gate (:decide) before anything commits
  to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore | DatomicStore, see `tobaccoops.store`)
    - the Advisor  (mock today; real LLM is the next seam --
                     `tobaccoops.advisor/Advisor` is already the
                     injection point, see its docstring)
    - the Phase    (0->3 rollout)

  One graph run = one tobacco-growing coordination operation. No
  unbounded inner loop -- each operation is auditable and checkpointed.
  A field's operating history is advanced by MANY operations
  (log-cultivation-record / schedule-field-operation /
  flag-crop-health-concern / order-supplies), each its own independent
  graph run, and every commit/hold/approval-rejected decision fact lands
  in `tobaccoops.store`'s append-only ledger (`store/append-ledger!`), so
  a field's full operating history is always a query over an immutable
  log.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human operator (farmer/agronomist)
  resumes it with a decision. `:flag-crop-health-concern` ALWAYS reaches
  this node when the Governor is clean -- see
  `tobaccoops.governor/always-escalate-ops`. Mirrors
  `cerealops.operation` (cloud-itonami-isic-0111) node/edge structure
  exactly, wired to this repo's own advisor/governor/phase/store.

  FIX (deferred-stub bug): this namespace used to be a plain
  `(let [proposal ... verdict ... disposition ...] {...})` synchronous
  function (`run-operation`) wrapped in a fn masquerading as a compiled
  graph (`build` returned `(fn invoke-operation [request context] ...)`)
  -- it never required `langgraph.graph` at all, despite `deps.edn`
  claiming a real `io.github.kotoba-lang/langgraph` dependency (which
  itself was only reachable from the never-invoked :dev :override-deps
  alias, not the real :deps map -- `:override-deps` overrides an
  *existing* :deps entry, it does not add a new one to an empty :deps
  map). `build` now returns a genuinely compiled
  `langgraph.graph/state-graph`, proven end-to-end by
  `test/tobaccoops/operation_test.cljc`."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [tobaccoops.advisor :as advisor]
            [tobaccoops.governor :as governor]
            [tobaccoops.phase :as phase]
            [tobaccoops.store :as store]))

(defn- commit-fact
  "The audit fact written when a proposal commits. `:record` carries the
  operational payload the advisor proposed (cultivation/curing/grading
  record, schedule, concern, supply order) -- tobaccoops has no separate
  stateful commit-record! entity beyond field registration, so the
  ledger fact itself is the durable record of what happened."
  [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:field-id request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)
   :record     (:value proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:field-id request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `tobaccoops.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor/-advise advisor store request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:field-id request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :always-escalate
                                          :else :low-confidence))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:field-id request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal]}]
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
