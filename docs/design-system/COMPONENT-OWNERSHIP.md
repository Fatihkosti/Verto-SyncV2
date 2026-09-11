# Component Ownership

| Component family | Owner | Rule |
|---|---|---|
| Foundation tokens and theme | `core/designsystem` | No feature imports. |
| Generic fields, buttons, cards, states | `core/designsystem` | Stateless, slot-based, 48dp targets. |
| Invoice line/item/editor semantics | `feature/invoice` | Uses shared primitives only. |
| Inventory item/stock semantics | `feature/inventory` | Uses shared primitives only. |
| Shipment/station/customs semantics | `feature/shipment` | Domain composition stays local. |
| Party role/profile semantics | `feature/party` | Domain text and actions stay local. |
| Reports charts and report-specific cards | `feature/reports` | Chart geometry remains local. |
| Settings/auth/employees forms | Owning feature | Shared fields and states are consumed, not copied. |
| PDF generation and rendering utilities | `core/export` or owning feature | Never in `core/designsystem`. |

`VertoLinearValueProgress` is a visual-only primitive: callers own progress calculation. `ClientAvatar` is owned by Invoice as `InvoiceClientAvatar`; `PaymentRateCard` is deleted as unused. Date-filter contracts remain local to Party (`PartyDateFilterChip` and `ClientDateFilterChip`). `AmountText` receives a caller-owned `currencyLabel`; it owns no currency literal. PDF utilities are owned by `core/export`.
