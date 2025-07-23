package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import it.eng.dome.revenue.engine.model.Reporting;
import it.eng.dome.revenue.engine.service.ReportingService;
import it.eng.dome.tmforum.tmf632.v4.ApiException;

@RestController
@RequestMapping("/revenue/dashboard")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/{relatedPartyId}")
    public List<Reporting> getDashboard(@PathVariable String relatedPartyId) throws ApiException, IOException {
        return reportingService.getDashboardReport(relatedPartyId);
    }
}
