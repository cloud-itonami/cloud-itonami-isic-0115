(ns tobaccoops.sim
  "Simple simulation/demo runner for the Tobacco-Growing Operations
  Coordinator actor. Used to validate that the actor flow compiles and
  basic proposal flow works. Mirrors `fibreops.sim`
  (cloud-itonami-isic-0116)."
  (:require [tobaccoops.operation :as operation]
            [tobaccoops.store :as store]))

(defn demo
  "Run a simple demo scenario: register a field, propose a cultivation
  record log, and check the disposition flow."
  []
  (let [;; Create store with a registered field
        st (store/mem-store
            {:initial-fields
             {"field-001"
              {:id "field-001"
               :name "Test Farm North Field"
               :tobacco-type "burley"}}})

        ;; Build actor
        actor (operation/build st)

        ;; Create a request to log a cultivation record
        request {:op :log-cultivation-record
                 :field-id "field-001"
                 :acreage 40
                 :tobacco-type "burley"
                 :leaf-grade "grade-a"
                 :record-type "curing"}

        ;; Context with phase 0 (simulation)
        context {:actor-id "tobacco-ops-01"
                 :role :farm-operator
                 :phase :phase-0}]

    (println "=== Tobacco-Growing Operations Coordinator Demo ===")
    (println "Demo field: field-001")
    (println "Request: log-cultivation-record")
    (println "Phase: phase-0 (simulation)")
    (println "Expected: escalate (phase-0 forces human review of all commits)")
    (println)
    (let [result (actor request context)]
      (println "Result disposition:" (:disposition result))
      result)))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
)
