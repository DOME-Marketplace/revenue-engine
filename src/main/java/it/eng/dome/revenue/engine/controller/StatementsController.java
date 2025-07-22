package it.eng.dome.revenue.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.service.TmfDataRetriever;

@RestController
//@RequiredArgsConstructor
@RequestMapping("revenue/statements")
public class StatementsController {
    
	protected final Logger logger = LoggerFactory.getLogger(StatementsController.class);
	
	@Autowired
    TmfDataRetriever tmfDataRetriever;

    public StatementsController() {
    }

//    @GetMapping("")
//    public ResponseEntity<List<RevenueStatement>> getAllStatements() {
//        try {
//            List<RevenueStatement> statements = statementService.loadAllStatements();
//            return ResponseEntity.ok(statements);
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }
    
//    @GetMapping("/{id}")
//    public ResponseEntity<RevenueStatement> getStatementsById(@PathVariable String id) {
//        try {
//        	RevenueStatement statement = statementService.findStatementById(id);
//            return ResponseEntity.ok(statement);
//        } catch (IOException e) {
//            return ResponseEntity.notFound().build(); 
//        }
//    }
    
}
