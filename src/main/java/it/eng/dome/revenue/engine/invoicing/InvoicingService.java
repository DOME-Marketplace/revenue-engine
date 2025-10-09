package it.eng.dome.revenue.engine.invoicing;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.brokerage.observability.info.Info;
import it.eng.dome.brokerage.utils.enumappers.TMF637EnumModule;
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
    public String invoicingServiceEndpoint;

    // a json mapper for outgoing calls
    private ObjectMapper outMapper;

    public InvoicingService() {
        this.initOutMapper();
    }

    private void initOutMapper() {
        this.outMapper = new ObjectMapper();
        // jsr310 time module
        this.outMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.outMapper.registerModule(new JavaTimeModule());
        // enum mapper for TMF637
        this.outMapper.registerModule(new TMF637EnumModule());
    }

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

            String outJson = this.outMapper.writeValueAsString(dto);

            RestClient defaultClient = RestClient.create();
            ResponseEntity<AppliedCustomerBillingRate[]> response = defaultClient.post()
                .uri(invoicingServiceEndpoint + "/invoicing/applyTaxes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(outJson)
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
                .uri(invoicingServiceEndpoint + "/invoicing/info")
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
