package it.eng.dome.revenue.engine.invoicing;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.JSON;
import it.eng.dome.brokerage.invoicing.dto.ApplyTaxesRequestDTO;

@Service
public class InvoicingService {
	
	private static final Logger logger = LoggerFactory.getLogger(InvoicingService.class);
	
	RestTemplate restTemplate = new RestTemplate();
	
//	@Value("${billing.invoicing_service}")
	public String invoicingService = "http://localhost:8380";
		

	public List<AppliedCustomerBillingRate> applyTaxees(Product product, List<AppliedCustomerBillingRate> acbrs) {
		// prepare the DTO to the invoicing service
		ApplyTaxesRequestDTO dto = new ApplyTaxesRequestDTO(product, acbrs);

		// invoke the invoicing service
		String json = this.billApplyTaxes(dto.toJson());

		// convert back json to acbrs
		AppliedCustomerBillingRate[] enrichedAcbrs = JSON.getGson().fromJson(json, AppliedCustomerBillingRate[].class);
		return Arrays.asList(enrichedAcbrs);
	}

	private String billApplyTaxes(String bill) {
		// TODO: check this whole method more carefully
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(bill , headers);
		logger.debug("Payload bill apply taxes received:\n" + bill);
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(invoicingService + "/invoicing/applyTaxes", request, String.class);
		
			if (response != null && response.getBody() != null) {
				logger.debug("Headers: " + response.getHeaders().toString());
				logger.debug("Body:\n" + response.getBody().toString());
				return response.getBody().toString();
			
			}else {
				logger.warn("Response: ", (response == null) ? response : response.getBody());
				logger.debug("Cannot retrieve the bill apply taxes from {}", invoicingService);
				return null;
			}
		} catch(Exception e) {
			logger.error("" + e.getStackTrace().toString());
			return "{}";
		}
	}

}