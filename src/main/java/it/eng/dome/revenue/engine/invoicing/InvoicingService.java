package it.eng.dome.revenue.engine.invoicing;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.brokerage.billing.dto.BillingResponseDTO;
import it.eng.dome.brokerage.observability.info.Info;
import it.eng.dome.brokerage.utils.enumappers.TMF637EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF678EnumModule;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

<<<<<<< HEAD
=======
@JsonInclude(JsonInclude.Include.NON_NULL)
class LocalApplyTaxesRequestDTO {

    @JsonProperty("product")
    private Product product;

    @JsonProperty("customerBill")
    private CustomerBill customerBill;

    @JsonProperty("appliedCustomerBillingRate")
    private List<AppliedCustomerBillingRate> appliedCustomerBillingRate;

    public Product getProduct() { 
    	return product; 
	}
    
    public void setProduct(Product product) { 
    	this.product = product; 
	}

    public CustomerBill getCustomerBill() {
    	return customerBill; 
	}
    
    public void setCustomerBill(CustomerBill customerBill) {
    	this.customerBill = customerBill; 
	}

    public List<AppliedCustomerBillingRate> getAppliedCustomerBillingRate() { 
    	return appliedCustomerBillingRate; 
	}
    
    public void setAppliedCustomerBillingRate(List<AppliedCustomerBillingRate> appliedCustomerBillingRate) {
    	this.appliedCustomerBillingRate = appliedCustomerBillingRate; 
	}
}

>>>>>>> parent of fd40fb4 (aligned to invoicing service and cleanup)
@Service
public class InvoicingService {

    private static final Logger logger = LoggerFactory.getLogger(InvoicingService.class);

    @Value("${billing.invoicing_service}")
    public String invoicingServiceEndpoint;

    private ObjectMapper outMapper;

    public InvoicingService() {
        this.initOutMapper();
    }

    private void initOutMapper() {
        this.outMapper = new ObjectMapper();
        this.outMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.outMapper.registerModule(new JavaTimeModule());
        this.outMapper.registerModule(new TMF637EnumModule());
        this.outMapper.registerModule(new TMF678EnumModule());
    }

    /**
     * Ensures every AppliedCustomerBillingRate has a non-null, non-empty ID.
     */
    private void ensureAcbrIds(List<AppliedCustomerBillingRate> acbrs) {
        for (AppliedCustomerBillingRate acbr : acbrs) {
            if (acbr.getId() == null || acbr.getId().isEmpty()) {
                acbr.setId(UUID.randomUUID().toString());
            }
        }
    }

    /**
     * Applies taxes to a list of ACBRs for a given product.
     * The invoicing service returns a JSON object containing a field "appliedCustomerBillingRate" 
     * which is a list of enriched ACBRs.
     */
    public BillingResponseDTO applyTaxes(CustomerBill customerBill, List<AppliedCustomerBillingRate> acbrs) throws ExternalServiceException {
        LocalApplyTaxesRequestDTO dto = new LocalApplyTaxesRequestDTO();
        dto.setCustomerBill(customerBill);
        dto.setAppliedCustomerBillingRate(acbrs);

        try {
<<<<<<< HEAD
            ensureAcbrIds(acbrs);

            Invoice tempInvoice = new Invoice(customerBill, acbrs);
            ObjectMapper mapper = this.outMapper;
            String outJson = mapper.writeValueAsString(List.of(tempInvoice));
=======
            String outJson = this.outMapper.writeValueAsString(dto);
>>>>>>> parent of fd40fb4 (aligned to invoicing service and cleanup)
            logger.debug("Sending payload to invoicing service: {}", outJson);

            RestClient client = RestClient.create();

            ResponseEntity<String> response = client.post()
                    .uri(invoicingServiceEndpoint + "/invoicing/applyTaxes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outJson)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);

            String bodyString = response.getBody();
            logger.debug("Raw invoicing response: {}", bodyString);

            BillingResponseDTO result = outMapper.readValue(bodyString, BillingResponseDTO.class);
            if (result == null) {
                logger.warn("Empty body from invoicing service, returning fallback BillingResponseDTO");
                return new BillingResponseDTO(customerBill, acbrs);
            }

            logger.info("Taxes successfully applied by invoicing service.");
            return result;

        } 
        catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON response from invoicing service: {}", e.getMessage());
            throw new ExternalServiceException("Unexpected response from Invoicing Service", e);
        }
    }

    /**
     * Calls the invoicing service endpoint for getting info.
     */
    public Info getInfo() {
        try {
            RestClient client = RestClient.create();
            ResponseEntity<Info> response = client.get()
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
