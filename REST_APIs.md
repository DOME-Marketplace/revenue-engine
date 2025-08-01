# Revenue Engine

**Version:** 1.2.1  
**Description:** Swagger REST APIs for the revenue-engine software  


## REST API Endpoints

### dev-controller
| Verb | Path | Task |
|------|------|------|
| POST | `/dev/revenue/to-acbr` | convertToACBR |
| POST | `/dev/revenue/to-acbr-list` | convertToACBRList |
| GET | `/dev/revenue/billingAccount/{relatedPartyId}` | getBillingAccountByRelatedParty |

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

### dev-organization-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/dev2/revenue/organizations/{referrerOrganizationId}/referrals` | listReferralsProviders |
| GET | `/dev2/revenue/organizations/{referralOrganizationId}/referrer` | getReferrerProvider |

