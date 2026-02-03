# Release Notes
 
**Release Notes** of the *Revenue Sharing Service*

### <code>1.5.0</code> :calendar: 03/02/2026
**New Features**
- Revenue section now uses rolling 1-month window for current period calculation
- Current period spans from today minus 1 month to today (e.g., 2026-01-03 - 2026-02-03)
- Current tier displays the highest tier applied in statements overlapping the current period
- Yearly total unchanged - remains subscription anniversary-based
**Bug fixes**
- Filter statements by period overlap instead of exact date match
- Fix current tier extraction to handle multiple overlapping statements
**Configuration**
- Scheduler frequency changed from hourly to monthly (6th of each month at 02:00)

### <code>1.4.0</code> :calendar: 19/01/2026
**New Features**
- Subscription plan descriptor (json)
  - Support for further custom time periods (‘CHARGE_PERIODS_X_TO_Y’)
  - new property `${chargePeriod.nr}` can be used in price/discount names and resolved at runtime.
**Bug fixes**
- Fixed bug in AbstractCalculator when evaluating preconditions for 'ignorePeriod' and 'validPeriod'

### <code>1.3.1</code> :calendar: 16/01/2026
**Improvements**
- Added sequential billNo in `TmfPersistenceService` class.

### <code>1.3.0</code> :calendar: 12/12/2025
**Enhancements**
- Enhanced computation in `ReportingService` class.
**Bug fixes**
- General bug fixing
- General cleanup

### <code>1.2.10</code> :calendar: 11/12/2025
**Enhancements**
- Retrieved CustomerBills from TMF in `ReportingService` class.

### <code>1.2.9</code> :calendar: 10/12/2025
**Bug fixes**
- Fixed null taxItem in `BillsService` class.

### <code>1.2.8</code> :calendar: 10/12/2025
**Improvements**
- Replace **RestTemplate** for **RestClient**.
**Enhancements**
- Alignment with new API of the invoicing service 2.0.3
**Bug fixes**
- General bug fixing
- General cleanup

### <code>1.2.7</code> :calendar: 27/11/2025
**New Features**
- Subscription plan descriptor (json)
  - Parsing of boolean expressions within some property values
  - Property resolution in ‘percent’
  - Support for further metrics (‘billedSellersBehindMarketplace’, ‘published-product-offerings’ and ‘published-selfservice-product-offerings’)
  - Support for further custom time periods (‘BETWEEN_date_AND_date’, ‘CHARGE_PERIOD_X’)
  - Support for ‘unitAmount’ when expressing prices/discounts
  - Renaming of ‘applicable*’ and ‘computation*’ properties
  - Aligning the plan.json to the latest version of DOME subscription plans
- Revenue Sharing dashboard
  - Distinct sections for CSPs and Federated Marketplaces
  - Showing meaningful messages for CSPs without subscription
- Healthcheck endpoint
  - Now checking all TMF APIs individually
  - Added response times for external APIs
- Development dashboard
  - New combined viewer for Invoices
  - Raw viewer for ACBRs and CBs
  - Misc UI enhancements

**Enhancements**
- Alignment with new API of the `invoicing service 2.0.0`.
- Increased caching for **TMF entities**.
- `JavaDoc` improved.
- General refactoring and cleanup.

**Bug fixes**
- General bug fixing.


### <code>1.2.6</code> :calendar: 24/10/2025
**Improvements**
- Usage of the new `Brokerage Utils` version: `2.2.0`.
- Add `TmfApiConfig` class to avoid loading the **TMFourm APIs** objects every time they are used in service classes.
- Usage of `AbstractHealthService` class from `Brokerage Utils` to manage **getInfo()** and **getHealth()** features.
- Add `TMF637EnumModule` class in the **JacksonModuleConfig** to *serialize* and *deserialize* the **TMForum enum types**.
- Set **cache duration** parameters in the `application.yaml` file.
**New Features**
- Set **persist settings** parameters in the `application.yaml` file. 
- Added `RevenueScheduler` class and `SchedulerConfig` class.
- Added `CorsConfig` class.


### <code>1.2.5</code> :calendar: 22/10/2025
**Bug Fixing**
- General bug fixes and minor performance optimizations.

**New Features**
- Added new methods in `ReportingService` to generate the DOME Operator dashboard.
- Replaced the `compute` package with the old `compute2`.
- Introduced for-each logic to support federated marketplace handling.

**Improvements**
- Enhanced tiering logic in the application of pricing rules.
- Improved exception handling.


### <code>1.2.4</code> :calendar: 09/10/2025
**Bug Fixing**
- General bug fixing and cleanup:
  - Refactored and cleaned up legacy code.
  - Minor fixes across multiple services.

**New Features**
- Added `InvoicingService` for apply taxes.
- Added `compute2` package for enhanced computational logic.
- Introduced `HealthService` for system health monitoring.
- Added `TmfPersistenceService` for data persistence.
- Developed `planResolver` to support dynamic token resolution in plans.
- Implemented tiering logic to support tax brackets.
- Added `IdUtils`.

**Improvements**
- Improved validation logic in `PlanValidator`.
- Improved cache usage.

### <code>1.2.3</code> :calendar: 23/09/2025
**Bug Fixing**
- General bug fixing:
	- Added parameter in `InvoicingService`.
	- Fixed filter for `RelatedParty`.

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