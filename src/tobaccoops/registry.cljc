(ns tobaccoops.registry
  "Pure validation functions for tobacco-growing operations. These are
  called by the Governor to independently verify proposal parameters --
  the LLM advisor's confidence is NOT sufficient to override these checks.
  Mirrors `fibreops.registry` (cloud-itonami-isic-0116) in shape, adding
  a `leaf-grade-unknown?` check (this crop family's own grading-specific
  measure: a tobacco cultivation record cites a leaf-grade code, and that
  code must be one of the actor's recognized closed vocabulary -- see
  `tobaccoops.facts/leaf-quality-grades`)."
  (:require [tobaccoops.facts :as facts]))

(defn cost-exceeds-threshold?
  "Independently verify a proposed spend against its category/default
  threshold. Inclusive at the boundary (exactly-at-threshold does not
  escalate)."
  [cost threshold]
  (> cost threshold))

(defn acreage-non-positive?
  "A logged planting/harvest/curing-batch acreage of zero or negative is
  not a real observation -- reject it as a HARD violation rather than
  silently accepting bad data into the cultivation record."
  [acreage]
  (<= acreage 0))

(defn leaf-grade-unknown?
  "A logged leaf-quality grade that isn't in the actor's recognized
  closed vocabulary (`tobaccoops.facts/leaf-quality-grades`) is not a
  plausible observation -- reject it as a HARD violation (mirrors
  `fibreops.registry/quality-grade-unknown?`: an independent structural
  plausibility check on a domain-specific field, not an agronomic
  judgment about the harvest's actual quality)."
  [grade]
  (not (contains? facts/leaf-quality-grades grade)))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))
