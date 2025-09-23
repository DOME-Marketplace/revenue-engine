package it.eng.dome.revenue.engine.invoicing;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import it.eng.dome.brokerage.invoicing.dto.ApplyTaxesRequestDTO;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.JSON;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@Service
public class InvoicingService {

    private static final Logger logger = LoggerFactory.getLogger(InvoicingService.class);

    RestTemplate restTemplate = new RestTemplate();

    @Value("${billing.invoicing_service}")
    public String invoicingService;

    /**
     * Applies taxes to a list of ACBRs for a given product.
     * The invoicing service returns a JSON object containing a field "appliedCustomerBillingRate" 
     * which is a list of enriched ACBRs.
     */
    public List<AppliedCustomerBillingRate> applyTaxees(Product product, List<AppliedCustomerBillingRate> acbrs) {
        // Prepare DTO
        ApplyTaxesRequestDTO dto = new ApplyTaxesRequestDTO(product, acbrs);

        // Call invoicing service
        String json = this.billApplyTaxes(dto.toJson());

        if (json == null || json.isEmpty()) {
            logger.warn("No response received. Returning original ACBRs.");
            return acbrs;
        }

        try {
            // Deserialize JSON array directly
            AppliedCustomerBillingRate[] enrichedAcbrs = JSON.getGson().fromJson(json, AppliedCustomerBillingRate[].class);
            return Arrays.asList(enrichedAcbrs);
        } catch (Exception e) {
            logger.error("Failed to parse JSON response from invoicing service: {}", e.getMessage());
            return acbrs;
        }
    }

    /**
     * Calls the invoicing service endpoint for applying taxes.
     */
    private String billApplyTaxes(String bill) {
		// TODO: check this whole method more carefully
    	
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(bill, headers);

        logger.debug("Payload bill apply taxes received:\n" + bill);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    invoicingService + "/invoicing/applyTaxes",
                    request,
                    String.class
            );

            if (response != null && response.getBody() != null) {
                logger.debug("Headers: " + response.getHeaders().toString());
                logger.debug("Body:\n" + response.getBody().toString());
                return response.getBody().toString();
            } else {
                logger.warn("Response was null or empty");
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception calling invoicing service: ", e);
            return null;
        }
    }


}
