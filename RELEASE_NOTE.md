# Release Notes
 
**Release Notes** of the *Revenue Engine* software:

### <code>1.2.3</code> :calendar: 23/09/2025
**Bug Fixing**
- General bug fixing:
	-Added parameter in InvoicingService
	-Fixed filter for RelatedParty
  
**Improvements**
- General bug fixes and minor optimizations in the RevenueProductMapper class.
### <code>1.2.2</code> :calendar: 22/09/2025
**Feature**
- Added a new mapper for handling ACBR and Invoice-Service calls for tax management.
- Implemented persistence logic for ACBR (AppliedCustomerBillingRate) and CustomerBill (CB) in TMF.
- Introduced caching to improve performance.
  
**Improvements**
- General bug fixes and minor optimizations in the RevenueProductMapper class.

### <code>1.2.1</code> :calendar: 01/08/2025
**Feature**
- Added following sections in Report:
	- `BillingHistory`.
	- `Discount earned`.
	- `Bill Previsioning`.
  
**Bug Fixing**
- General bug fixing and minor optimizations of the reporting **dashboard** featuring:
	- `Subscription plan` overview.
	- `Revenue` section.

**Improvements**
* Generate automatic `REST_APIs.md` file from **Swagger APIs** using the `generate-rest-apis` profile.

### <code>1.2.0</code> :calendar: 25/07/2025
**Feature**
* Aligned with `[2.1.0, 2.2.0)` version of `Brokerage Utils`.
* Display `ENV VARs` in the Listener at beginning.
 
 
### <code>0.0.6</code> :calendar: 21/07/2025
**Feature**
- First operational version of reporting **dashboard** with:
  - Subscription plan overview
  - Revenue summary
  
  
### <code>0.0.5</code> :calendar: 17/07/2025
**Feature**
- Completed implementation of `DiscountCalculator` and `PriceCalculator`:
  - Full support for `computationBase`, `applicableBase`, `applicableBaseRange`.
  - Bundle strategies: `CUMULATIVE`, `ALTERNATIVE_HIGHER`, `ALTERNATIVE_LOWER`.
  - Supports both `amount` and `percentage` discounts.
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