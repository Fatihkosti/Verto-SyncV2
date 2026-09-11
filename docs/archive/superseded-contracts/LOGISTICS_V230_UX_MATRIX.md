# Logistics v230 — Mandatory Planning UX Matrix

Status: ACTIVE
Owner: feature/shipment
Scope: v230 shipment definition, sources, route planning, templates, and shipping contacts.

Every changed planning surface must be checked at:

- Width: 320dp, 360dp, 412dp.
- Font scale: 1.0, 1.3, 2.0.
- Direction: RTL.
- Theme: Light and Dark.
- TalkBack: focus order, field labels, button names, picker choices, validation feedback.

Acceptance:

- Bottom Back/Next actions remain reachable without horizontal clipping.
- Fields may grow vertically at large font scale; primary actions must not clip.
- Interactive controls keep at least the canonical 48dp touch target.
- Shipment number remains read-only.
- Step 2 exposes supplier/invoices only; no item allocation UI.
- Planning does not request carrier, packages, weight, cost, or documents.
- Customs can be "لا توجد جمارك" or one intermediate station.
- Mixed mode defers per-leg transport mode until execution.
