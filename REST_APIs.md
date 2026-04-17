# Revenue Engine

**Version:** 1.6.0  
**Description:** Swagger REST APIs for the revenue-engine software  


## REST API Endpoints

### Revenue Engine Plan Controller
| Verb | Path | Task |
|------|------|------|
| POST | `/revenue/plans/validate` | validatePlan |
| GET | `/revenue/plans` | getAllPlans |
| GET | `/revenue/plans/{planId}` | getPlanById |
| GET | `/revenue/plans/{planId}/validate` | validatePlan_1 |
| GET | `/revenue/plans/{planId}/subscriptions` | getSubscriptionsByPlanId |

### Revenue Engine Subscriptions Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/subscriptions` | getAllSubscriptions |
| GET | `/revenue/subscriptions/{subscriptionId}` | getSubscription |
| GET | `/revenue/subscriptions/{subscriptionId}/statements` | statementCalculator |
| GET | `/revenue/subscriptions/{subscriptionId}/statements/itemsonly` | statementItems |
| GET | `/revenue/subscriptions/{subscriptionId}/bills` | getBillPeriods |

### Revenue Engine Persistence Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/persistence/persist` | peristEverything |
| GET | `/revenue/persistence/persist/subscription/{subscriptionId}` | persistForSubscription |
| GET | `/revenue/persistence/persist/revenuebill/{revenueBillId}` | persistRevenueBill |
| GET | `/revenue/persistence/persist/provider/{providerId}` | persistForProvider |

### Revenue Engine Info Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/info` | getInfo |
| GET | `/revenue/health` | getHealth |

### Revenue Engine Develop Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/dev/organizations` | listOrganizations |
| GET | `/revenue/dev/organizations/{referrerOrganizationId}/referrals` | listReferralsProviders |
| GET | `/revenue/dev/organizations/{referralOrganizationId}/referrer` | getReferrerProvider |
| GET | `/revenue/dev/organizations/{organizationId}/soldProducts` | listSoldProducts |
| GET | `/revenue/dev/organizations/{organizationId}/purchasedProducts` | listPurchasedProducts |
| GET | `/revenue/dev/organizations/{organizationId}/customerbills` | listOrganizationTransactions |
| GET | `/revenue/dev/invoices/{customerBillId}` | getInvoice |
| GET | `/revenue/dev/customerbills/{customerBillId}` | getCustomerBill |
| GET | `/revenue/dev/customerbills/{customerBillId}/acbr` | getACBRs |

### Revenue Engine Dashboard Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/dashboard/{relatedPartyId}` | getDashboard |

### Revenue Engine Bills Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/bills/{revenueBillId}/cb` | getCustomerBillByRevenueBillId |
| GET | `/revenue/bills/{revenueBillId}/acbr` | getACBRsByRevenueBillId |
| GET | `/revenue/bills/{billId}` | getBillPeriods_1 |

### Revenue Engine Purchasing Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/billing/instantBill` | instantBill |
| GET | `/revenue/billing/bill` | bill |

