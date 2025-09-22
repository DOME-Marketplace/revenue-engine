# Revenue Engine

**Version:** 1.2.2  
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
| GET | `/revenue/plans/{planId}/subscriptions` | getSubscriptionsByPlanId |

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
| GET | `/revenue/bills/{billId}` | getBillPeriods_1 |
| GET | `/revenue/bills/{revenueBillId}/cb` | getCustomerBillByRevenueBillId |
| GET | `/revenue/bills/{revenueBillId}/acbr` | getACBRsByRevenueBillId |

### persistence-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/revenue/persistence/persist` | peristEverything |
| GET | `/revenue/persistence/subscription/{subscriptionId}` | peristForSubscription |
| GET | `/revenue/persistence/revenueBill/{revenueBillId}` | peristRevenueBill |
| GET | `/revenue/persistence/provider/{providerId}` | peristForProvider |

### dev-organization-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/dev2/revenue/organizations/{referrerOrganizationId}/referrals` | listReferralsProviders |
| GET | `/dev2/revenue/organizations/{referralOrganizationId}/referrer` | getReferrerProvider |

