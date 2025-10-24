# Revenue Engine

**Version:** 1.2.6  
**Description:** Swagger REST APIs for the revenue-engine software  


## REST API Endpoints

### plans-controller
| Verb | Path | Task |
|------|------|------|
| POST | `/revenue/plans/validate` | validatePlan |
| GET | `/revenue/plans` | getAllPlans |
| GET | `/revenue/plans/{planId}` | getPlanById |
| GET | `/revenue/plans/{planId}/validate` | validatePlan_1 |
| GET | `/revenue/plans/{planId}/subscriptions` | getSubscriptionsByPlanId |

### subscriptions-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/subscriptions` | getAllSubscriptions |
| GET | `/revenue/subscriptions/{subscriptionId}` | getSubscription |
| GET | `/revenue/subscriptions/{subscriptionId}/statements` | statementCalculator |
| GET | `/revenue/subscriptions/{subscriptionId}/statements/itemsonly` | statementItems |
| GET | `/revenue/subscriptions/{subscriptionId}/bills` | getBillPeriods |

### persistence-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/persistence/persist` | peristEverything |
| GET | `/revenue/persistence/persist/subscription/{subscriptionId}` | persistForSubscription |
| GET | `/revenue/persistence/persist/revenuebill/{revenueBillId}` | persistRevenueBill |
| GET | `/revenue/persistence/persist/provider/{providerId}` | persistForProvider |

### Revenue Engine Controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/info` | getInfp |
| GET | `/revenue/health` | getHealth |

### dev-organization-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/dev/organizations` | listOrganizations |
| GET | `/revenue/dev/organizations/{referrerOrganizationId}/referrals` | listReferralsProviders |
| GET | `/revenue/dev/organizations/{referralOrganizationId}/referrer` | getReferrerProvider |
| GET | `/revenue/dev/organizations/{organizationId}/soldProducts` | listSoldProducts |
| GET | `/revenue/dev/organizations/{organizationId}/purchasedProducts` | listPurchasedProducts |
| GET | `/revenue/dev/organizations/{organizationId}/customerbills` | listOrganizationTransactions |
| GET | `/revenue/dev/customerbills/{customerBillId}` | getCustomerBill |
| GET | `/revenue/dev/customerbills/{customerBillId}/acbr` | getACBRs |

### reporting-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/dashboard/{relatedPartyId}` | getDashboard |

### bills-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/bills/{revenueBillId}/cb` | getCustomerBillByRevenueBillId |
| GET | `/revenue/bills/{revenueBillId}/acbr` | getACBRsByRevenueBillId |
| GET | `/revenue/bills/{billId}` | getBillPeriods_1 |

### preview-and-billing-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/billing/instantBill` | instantBill |
| GET | `/revenue/billing/bill` | bill |

