# Business Model: Tobacco-Growing Operations Coordinator

## Classification

- Repository: `cloud-itonami-isic-0115`
- ISIC Rev. 4: `0115`
- Industry: Growing of tobacco
- Social impact: food-security, rural-employment, environmental-stewardship

## Customer

- Small-to-medium tobacco farms (flue-cured/bright, burley, oriental,
  dark-fired, cigar-wrapper)
- Tobacco cooperatives and contract growers
- Diversified row-crop operations that include tobacco acreage
- Smallholder tobacco producers (extension-service integrations)

## Offer

- Field management and record-keeping (planting/curing/grading batch and
  leaf-quality data)
- Planting/topping/harvesting/curing/grading scheduling coordination
- Crop-health and pest/disease/curing-defect tracking (e.g. tobacco
  hornworm, blue mold, barn rot)
- Supply procurement coordination
- Audit trail and transparency

## Revenue

- SaaS subscription (per-hectare-per-season pricing)
- Supply chain integration fees
- API access for agronomist/extension-service partners
- Data analytics and reporting add-ons

## Trust Controls

- No direct field-equipment operation without human sign-off
- No finalized curing-barn temperature decisions by the actor
- No finalized pesticide-application decisions by the actor
- All field/curing-operation scheduling proposals are proposals, not commands
- Field registration is required before any operation
- All crop-health/curing-defect concerns are automatically escalated
- High-cost supply orders require approval
- Logged leaf-quality grades are independently verified against a closed
  vocabulary before a record proposal is considered
- Audit ledger is append-only and never editable

## What we do NOT do

- **Agronomic decisions** (what/when/how much to plant, top, harvest, or
  cure) — the farmer/agronomist decides
- **Curing-barn temperature decisions** — the farmer/curing-operator decides
- **Pesticide-application decisions** — the agronomist/farmer decides
- **Direct field-equipment operation** — the robot manages records and logistics only
- **Economic decisions** (crop mix, marketing, land use) — remain human authority

## Supported Operations

### Cultivation Record Logging
- Planting records (tobacco-type, acreage, date)
- Curing-batch records
- Leaf-quality grade records (closed vocabulary — see `tobaccoops.facts`)
- Field-condition notes (logging only, not decision-making)

### Field/Curing-Operation Scheduling
- Schedule planting, topping, harvesting (priming), curing, grading windows
- Track equipment/labor/curing-barn availability
- Propose follow-up field visits (not order them directly)

### Crop-Health/Curing-Defect Concern Escalation
- Flag suspected pest infestation (e.g. tobacco hornworm)
- Report disease symptoms (e.g. blue mold) or drought stress
- Report curing defects (e.g. barn rot, house burn)
- Automatic escalation to farmer/agronomist

### Supply Procurement
- Fertilizer orders
- Pesticide orders
- Curing-fuel orders (propane/wood/coal for flue/fire curing barns)
- Cost threshold escalation for large orders
