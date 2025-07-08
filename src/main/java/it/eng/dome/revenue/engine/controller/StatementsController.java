package it.eng.dome.revenue.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.service.TmfDataRetriever;

@RestController
//@RequiredArgsConstructor
@RequestMapping("/dev2/revenue")
public class StatementsController {
    
	protected final Logger logger = LoggerFactory.getLogger(StatementsController.class);

	@Autowired
    TmfDataRetriever tmfDataRetriever;

    @Autowired
    public StatementsController() {
    }

}
