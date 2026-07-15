(ns tobaccoops.facts
  "Reference facts for tobacco-growing operations coordination: supply
  category cost policy, tobacco-type classification, field/curing-operation
  vocabulary, and a closed leaf-quality-grade vocabulary. This namespace
  contains pure lookup functions for domain reference data -- the Governor
  and Advisor consult these instead of inventing thresholds. Mirrors
  `fibreops.facts` (cloud-itonami-isic-0116) in shape, adapted to tobacco:
  a leaf crop grown for curing and grading rather than direct fibre or
  grain use, whose field-operation calendar includes topping (removing
  the flower head to concentrate leaf growth) and curing (post-harvest,
  barn/flue/air/fire/sun) alongside the planting/harvesting/grading
  operations common to row crops.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (farmer/ops-manager). Tobacco cultivation's distinctive
  high-cost supply category is curing fuel (propane/wood/coal for
  flue/fire curing barns), priced above routine fertilizer/pesticide
  inputs."
  {"fertilizer"
   {:id "fertilizer" :name "肥料" :cost-threshold 500}

   "pesticide"
   {:id "pesticide" :name "農薬" :cost-threshold 500}

   "curing-fuel"
   {:id "curing-fuel" :name "乾燥用燃料" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def tobacco-types
  "Tobacco types this actor's cultivation records may cover (ISIC 0115:
  growing of tobacco -- flue-cured/bright, burley, oriental, dark-fired,
  and cigar-wrapper tobacco, distinguished primarily by curing method.
  Other leaf/fibre crops are out of scope, ISIC 0116/0119)."
  {"flue-cured"    {:id "flue-cured" :name "黄色種（フルーキュアド）"}
   "burley"        {:id "burley" :name "バーレー種"}
   "oriental"      {:id "oriental" :name "東洋種（オリエント）"}
   "dark-fired"    {:id "dark-fired" :name "ダークファイアード種"}
   "cigar-wrapper" {:id "cigar-wrapper" :name "葉巻用ラッパー種"}})

(defn tobacco-type-by-id [id]
  (get tobacco-types id))

(def field-operation-types
  "Reference set of field/curing-operation types this actor's
  schedule-field-operation proposals commonly cover, spanning the
  pre-harvest topping step used to remove the flower head and concentrate
  leaf growth, the staged priming/harvesting step (leaves are picked in
  multiple passes as they ripen from the bottom of the stalk upward), and
  the post-harvest curing step (barn/flue/air/fire/sun, depending on
  tobacco type) that must complete before grading. Informational only --
  NOT a validated enum; the advisor/operator may propose other
  operation-type strings and the Governor does not reject unlisted
  values here."
  #{"planting" "topping" "harvesting" "curing" "grading"})

(def leaf-quality-grades
  "Closed set of recognized leaf-quality grade codes a cultivation record's
  :leaf-grade may cite -- independently verified by the Governor.
  Leaf-quality grading spans commodity-specific systems (USDA flue-cured/
  burley grade schedules by stalk position, color and quality; oriental
  and dark-fired have their own regional grading conventions); this is a
  generic closed vocabulary this actor's cultivation records use to
  record a graded outcome, not a physical measurement or a substitute for
  the commodity-specific standard."
  #{"premium" "grade-a" "grade-b" "grade-c" "below-grade" "ungraded"})
