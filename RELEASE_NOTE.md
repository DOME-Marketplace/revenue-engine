# Release Notes
 
**Release Notes** of the *Revenue Engine* software:
 
### <code>1.2.0</code> :calendar: 24/07/2025 (WIP)
**Feature**
* Aligned with `[2.1.0, 2.2.0)` version of `Brokerage Utils`.
* Display `ENV VARs` in the Listener at beginning.
 
 
### <code>0.0.6</code> :calendar: 21/07/2025
**Feature**
- First operational version of reporting dashboard with:
  - Subscription plan overview
  - Revenue summary
  
  
### <code>0.0.5</code> :calendar: 17/07/2025
**Feature**
- Completed implementation of `DiscountCalculator` and `PriceCalculator`:
  - Full support for `computationBase`, `applicableBase`, `applicableBaseRange`.
  - Bundle strategies: `CUMULATIVE`, `ALTERNATIVE_HIGHER`, `ALTERNATIVE_LOWER`.
  - Supports both `amount` and `percentage` discounts.
- Inheritance of `referencePeriod` and other null values from parent components.
- Discounts now integrated into `PriceCalculator`.
 
**Refactor**
- Unified logic for price and discount computation in their respective calculators.
- Currency consistency and proper naming propagation across nested `RevenueItem`s.
 
 
### <code>0.0.4</code> :calendar: 10/07/2025
**Feature**
- Introduced core model classes:
  - `RevenueItem`, `RevenueStatement`, `SubscriptionTimeHelper`.
- Recursive revenue aggregation for nested pricing/discount components.
- `RevenueStatement` includes readable description output.
 
 
### <code>0.0.3</code> :calendar: 03/07/2025
**Feature**
- Initial `PriceCalculator` and `DiscountCalculator` implementation:
  - Fixed amounts and percentage-based computation.
  - Bundle logic: cumulative, highest, lowest selection.
  - Support for `computationBase`, `applicableBase`, `applicableBaseRange`.
 
 
### <code>0.0.2</code> :calendar: 30/06/2025
**Features**
- Implemented REST endpoints:
  - `GET /subscription`
  - `GET /plan`
- JSON validation of requests/responses.
 
 
### <code>0.0.1</code> :calendar: 19/06/2025
**Feature**
* Init project.