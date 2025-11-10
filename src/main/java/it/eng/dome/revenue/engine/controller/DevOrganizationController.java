package it.eng.dome.revenue.engine.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.service.DevDashboardService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

@RestController
@RequestMapping("/revenue/dev")
public class DevOrganizationController {
    
	protected final Logger logger = LoggerFactory.getLogger(DevOrganizationController.class);

	@Autowired
    TmfDataRetriever tmfDataRetriever;

    @Autowired
    DevDashboardService dashboardService;

    public DevOrganizationController() {
        // Constructor for dependency injection
    }

    @GetMapping("organizations")
    public ResponseEntity<List<Organization>> listOrganizations() {
        try {
            List<Organization> organizations = dashboardService.listOrganizations();
            return ResponseEntity.ok(organizations);
        } catch (Exception e) {
            logger.error("Error retrieving organizations {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("organizations/{organizationId}/customerbills")
    public ResponseEntity<List<CustomerBill>> listOrganizationTransactions(@PathVariable String organizationId) {
        try {
            List<CustomerBill> bills = dashboardService.listOrganizationTransactions(organizationId);
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
            logger.error("Error retrieving customer bills {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("organizations/{organizationId}/purchasedProducts")
    public ResponseEntity<List<Product>> listPurchasedProducts(@PathVariable String organizationId) {
        try {
            List<Product> products = this.dashboardService.getPurchasedProducts(organizationId);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error retrieving purchased products {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("organizations/{organizationId}/soldProducts")
    public ResponseEntity<List<Product>> listSoldProducts(@PathVariable String organizationId) {
        try {
            List<Product> products = this.dashboardService.getSoldProducts(organizationId);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error retrieving sold products {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    
    
    @GetMapping("customerbills/{customerBillId}")
    public ResponseEntity<CustomerBill> getCustomerBill(@PathVariable String customerBillId) {
        try {
            CustomerBill bill = dashboardService.getCustomerBill(customerBillId);
            return ResponseEntity.ok(bill);
        } catch (Exception e) {
            logger.error("Error retrieving customer bill {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("customerbills/{customerBillId}/acbr")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getACBRs(@PathVariable String customerBillId) {
        try {
            List<AppliedCustomerBillingRate> acbrs = dashboardService.getAppliedCustomerBillingRates(customerBillId);
            return ResponseEntity.ok(acbrs);
        } catch (Exception e) {
            logger.error("Error retrieving ACBRs {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("invoices/{customerBillId}")
    public ResponseEntity<Map<String, Object>> getInvoice(@PathVariable String customerBillId) {
        try {
            Map<String, Object> invoice = dashboardService.getInvoice(customerBillId);
            return ResponseEntity.ok(invoice);
        } catch (Exception e) {
            logger.error("Error while generating the invoice {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("organizations/{referrerOrganizationId}/referrals")
    public ResponseEntity<List<Organization>> listReferralsProviders(@PathVariable String referrerOrganizationId) {
        try {
            List<Organization> referrals = tmfDataRetriever.listReferralsProviders(referrerOrganizationId);

            if (referrals == null || referrals.isEmpty()) {
                logger.info("No referrals found for Organization with ID {}", referrerOrganizationId);
                return ResponseEntity.ok(new ArrayList<>());
            }

            return ResponseEntity.ok(referrals);
        } catch (Exception e) {
            logger.error("Error retrieving referrals for Organization with ID {}: {}", referrerOrganizationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("organizations/{referralOrganizationId}/referrer")
    public ResponseEntity<Organization> getReferrerProvider(@PathVariable String referralOrganizationId) {
        try {
            Organization referrer = tmfDataRetriever.getReferrerProvider(referralOrganizationId);

            if (referrer == null) {
                logger.info("Referrer not found for Organization with ID {}", referralOrganizationId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(referrer);
        } catch (Exception e) {
            logger.error("Error retrieving referrer for Organization with ID {}: {}", referralOrganizationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}