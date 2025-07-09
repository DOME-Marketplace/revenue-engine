package it.eng.dome.revenue.engine.controller;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RecurringPeriod;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.TimePeriod;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;

@RestController
//@RequiredArgsConstructor
@RequestMapping("/dev2/revenue")
public class SubscriptionsController {
    
	protected final Logger logger = LoggerFactory.getLogger(StatementsController.class);

	@Autowired
    TmfDataRetriever tmfDataRetriever;

    @Autowired
    public SubscriptionsController() {
    }

    @GetMapping("/subscriptions/")
    public ResponseEntity<List<Subscription>> getAllSubscriptions() {
        try {
            List<Subscription> subscriptions = new ArrayList<>();
            subscriptions.add(this.createFakeSubscription(UUID.randomUUID().toString()));
            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<Subscription> getSubscription(@PathVariable String subscriptionId) {
        if(Math.random() < 0.1) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            return ResponseEntity.ok(this.createFakeSubscription(subscriptionId));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscriptions/{subscriptionId}/statements")
    public ResponseEntity<List<RevenueStatement>> sellerStatements(@PathVariable String subscriptionId) {
        try {
            List<RevenueStatement> statements = new ArrayList<>();

            Subscription subscription = this.createFakeSubscription(subscriptionId);

            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);

            for(TimePeriod tp : timeHelper.getChargePeriodTimes()) {
                statements.add(new RevenueStatement(subscription, tp));
            }

            return ResponseEntity.ok(statements);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/organizations/{referrerOrganizationId}/referrals")
    public ResponseEntity<List<Organization>> listReferralsProviders(@PathVariable String referrerOrganizationId) {
        try {
            return ResponseEntity.ok(tmfDataRetriever.listReferralsProviders(referrerOrganizationId));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/organizations/{referralOrganizationId}/referrer")
    public ResponseEntity<Organization> getReferrerProvider(@PathVariable String referralOrganizationId) {
        try {
            return ResponseEntity.ok(tmfDataRetriever.getReferrerProvider(referralOrganizationId));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Subscription createFakeSubscription(String subscriptionId) {
        Subscription subscription = new Subscription();
        subscription.setName("Fake Subscription");
        subscription.setStatus("ACTIVE");
        subscription.setId(subscriptionId);
        subscription.setStartDate(OffsetDateTime.now().minusMonths(47));
        subscription.setPlan(this.createFakeSubscriptionPlan(UUID.randomUUID().toString()));
        subscription.setRelatedParties(new ArrayList<>());

        return subscription;
    }

    private SubscriptionPlan createFakeSubscriptionPlan(String planId) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(planId);
        plan.setName("Fake Plan");
        plan.setDescription("This is a test subscription plan.");
        plan.setContractDurationPeriodType(RecurringPeriod.YEAR);
        plan.setContractDurationLength(1);

        Price bundle = new Price();
        bundle.setName("Bundle Price");
        bundle.setIsBundle(true);

        Price price = new Price();
        price.setRecurringChargePeriodType(RecurringPeriod.YEAR);
        price.setRecurringChargePeriodLength(1);

        Price anotherPrice = new Price();
        anotherPrice.setRecurringChargePeriodType(RecurringPeriod.MONTH);
        anotherPrice.setRecurringChargePeriodLength(13);

        List<Price> prices = List.of(price, anotherPrice);
        bundle.setPrices(prices);
        plan.setPrice(bundle);

        return plan;
    }

}