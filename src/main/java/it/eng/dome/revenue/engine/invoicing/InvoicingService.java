package it.eng.dome.revenue.engine.invoicing;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.dome.revenue.engine.utils.health.Info;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@JsonInclude(JsonInclude.Include.NON_NULL)
class LocalApplyTaxesRequestDTO {
	
	@JsonProperty("product")
	private Product product;
   
	@JsonProperty("appliedCustomerBillingRate")
	private List<AppliedCustomerBillingRate> appliedCustomerBillingRate;

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public List<AppliedCustomerBillingRate> getAppliedCustomerBillingRate() {
		return appliedCustomerBillingRate;
	}

	public void setAppliedCustomerBillingRate(List<AppliedCustomerBillingRate> appliedCustomerBillingRate) {
		this.appliedCustomerBillingRate = appliedCustomerBillingRate;
	}
}

@Service
public class InvoicingService {

    private static final Logger logger = LoggerFactory.getLogger(InvoicingService.class);

    @Value("${billing.invoicing_service}")
    public String invoicingService;

    /**
     * Applies taxes to a list of ACBRs for a given product.
     * The invoicing service returns a JSON object containing a field "appliedCustomerBillingRate" 
     * which is a list of enriched ACBRs.
     */
    public List<AppliedCustomerBillingRate> applyTaxees(Product product, List<AppliedCustomerBillingRate> acbrs) {

        // Prepare DTO
        LocalApplyTaxesRequestDTO dto = new LocalApplyTaxesRequestDTO();
        dto.setProduct(product);
        dto.setAppliedCustomerBillingRate(acbrs);

        try {
            RestClient defaultClient = RestClient.create();
            ResponseEntity<AppliedCustomerBillingRate[]> response = defaultClient.post()
                .uri(invoicingService + "/invoicing/applyTaxes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(AppliedCustomerBillingRate[].class);

            return Arrays.asList(response.getBody());
        } catch (Exception e) {
            logger.error("Failed to parse JSON response from invoicing service: {}", e.getMessage());
            return acbrs;
        }
    }


    /**
     * Calls the invoicing service endpoint for getting info.
     */
    public Info getInfo() throws Exception {
        try {
            RestClient defaultClient = RestClient.create();
            ResponseEntity<Info> response = defaultClient.get()
                .uri(invoicingService + "/invoicing/info")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(Info.class);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Exception calling invoicing service: ", e);
            throw(e);
        }
    }    

}
