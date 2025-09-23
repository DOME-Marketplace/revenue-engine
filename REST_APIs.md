# Revenue Engine

**Version:** 1.2.3  
**Description:** Swagger REST APIs for the revenue-engine software  


## REST API Endpoints

### subscriptions-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/subscriptions` | getAllSubscriptions |
| GET | `/revenue/subscriptions/{subscriptionId}` | getSubscription |
| GET | `/revenue/subscriptions/{subscriptionId}/statements` | statementCalculator |
| GET | `/revenue/subscriptions/{subscriptionId}/statements/itemsonly` | statementItems |
| GET | `/revenue/subscriptions/{subscriptionId}/bills` | getBillPeriods |

### plans-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/plans` | getAllPlans |
| GET | `/revenue/plans/{planId}` | getPlanById |
| GET | `/revenue/plans/{planId}/validate` | validatePlan |
| GET | `/revenue/plans/{planId}/subscriptions` | getSubscriptionsByPlanId |

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
| GET | `/revenue/info` | getInfo |

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

### dev-organization-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/dev2/revenue/organizations/{referrerOrganizationId}/referrals` | listReferralsProviders |
| GET | `/dev2/revenue/organizations/{referralOrganizationId}/referrer` | getReferrerProvider |

