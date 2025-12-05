package it.eng.dome.revenue.engine.controller;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.dome.brokerage.billing.dto.BillingRequestDTO;
//import it.eng.dome.brokerage.billing.dto.BillingPreviewRequestDTO;

//import it.eng.dome.tmforum.tmf622.v4.model.ProductOrder;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf637.v4.model.Product;


/**
 * This is the public interface to support the purchasing (subscription) process and for regular billing.
 * It must include the operations exposed by the BillingProxy for:
 * - preview
 * - bill
 * - instantBill
 * However, since the revenue is managed by an internal scheduling policy, empty responses are provided to the billing scheduler,
 * so that no cb/acbr are created by him.
 */

@RestController
@RequestMapping("/revenue/billing/")
@Tag(name = "Revenue Engine Purchasing Controller", description = "APIs to manage purchasing (subscription) process and for regular billing")
public class PreviewAndBillingController {

	protected final Logger logger = LoggerFactory.getLogger(PreviewAndBillingController.class);
    
    /*
    @GetMapping("previewPrice")
    public ProductOrder previewPrice(BillingPreviewRequestDTO request) {
        try {
            // Empty implementation: return the order as received
            // For a proper implementation, create a service/operation doing this job by leveraging the existing bills services.
            return ResponseEntity.ok(request.getProductOrder());
        } catch(Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    */

    @GetMapping("bill")
    public ResponseEntity<List<AppliedCustomerBillingRate>> bill(BillingRequestDTO request) {
        try {
            // empty implementation
            return ResponseEntity.ok(new ArrayList<AppliedCustomerBillingRate>());
        } catch(Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("instantBill")
    public ResponseEntity<List<AppliedCustomerBillingRate>> instantBill(Product product) {
        try {
            // empty implementation
            return ResponseEntity.ok(new ArrayList<AppliedCustomerBillingRate>());
        } catch(Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
