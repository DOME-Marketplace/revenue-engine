package it.eng.dome.revenue.engine.invoicing;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.brokerage.model.Invoice;
import it.eng.dome.brokerage.observability.info.Info;
import it.eng.dome.brokerage.utils.enumappers.TMF637EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF678EnumModule;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;


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
        // enum mapper for TMF678
        this.outMapper.registerModule(new TMF678EnumModule());
    }
    /**
     * Applies taxes to a list of ACBRs for a given product.
     * The invoicing service returns a JSON object containing a field "appliedCustomerBillingRate" 
     * which is a list of enriched ACBRs.
     * @throws Exception 
     */
    public Invoice applyTaxes(CustomerBill customerBill, List<AppliedCustomerBillingRate> acbrs) throws ExternalServiceException {
        try {
            Invoice tempInvoice = new Invoice(customerBill, acbrs);
            ObjectMapper mapper = this.outMapper;
            String outJson = mapper.writeValueAsString(List.of(tempInvoice));
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

            List<Invoice> invoicesResult = mapper.readValue(bodyString,
                    mapper.getTypeFactory().constructCollectionType(List.class, Invoice.class));

            if (invoicesResult.isEmpty()) {
                throw new ExternalServiceException("Invoicing service returned empty result");
            }

            logger.info("Taxes successfully applied by invoicing service.");
            return invoicesResult.get(0);

        } catch (Exception e) {
            logger.error("Error calling invoicing service: {}", e.getMessage());
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
