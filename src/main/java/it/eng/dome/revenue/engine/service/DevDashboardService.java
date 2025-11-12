package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.comparator.CustomerBillComparator;
import it.eng.dome.revenue.engine.model.comparator.OrganizationComparator;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.TaxItem;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class DevDashboardService {

//    private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
	private TmfCachedDataRetriever tmfDataRetriever;

    @Autowired
	private BillsService billService;

    public List<Organization> listOrganizations() throws Exception {
        List<Organization> orgs = tmfDataRetriever.getOrganizations();
        List<Organization> mutableOrgs = new ArrayList<>(orgs); // copy mutable list
        Collections.sort(mutableOrgs, new OrganizationComparator());
        return mutableOrgs;
    }

    public List<CustomerBill> listOrganizationTransactions(String sellerId) throws Exception {

        // considering only the last year
        TimePeriod tp = new TimePeriod();
        tp.setEndDateTime(OffsetDateTime.now());
        tp.setStartDateTime(OffsetDateTime.now().minusYears(1));

        // sort transactions by billDate
        List<CustomerBill> bills = tmfDataRetriever.retrieveCustomerBills(sellerId, null, tp);
        bills.sort(new CustomerBillComparator());

        return bills;
    }

    public CustomerBill getCustomerBill(String customerbillId) throws Exception {
        return tmfDataRetriever.getCustomerBill(customerbillId);
    }

    public List<AppliedCustomerBillingRate> getAppliedCustomerBillingRates(String customerbillId) throws Exception {
        return tmfDataRetriever.getACBRsByCustomerBillId(customerbillId);
    }

    public List<Product> getPurchasedProducts(String buyerId) throws ExternalServiceException, ExternalServiceException {
        List<Product> purchasedProducts = new ArrayList<>();
        // Batch processing of all active products
        tmfDataRetriever.fetchActiveProducts(50,
            product -> {
                // Keep only products where the buyer has the BUYER role
                if (RelatedPartyUtils.productHasPartyWithRole(product, buyerId, Role.BUYER)) {
                    purchasedProducts.add(product);
                }
        });

        return purchasedProducts;
    }

    public List<Product> getSoldProducts(String sellerId) throws ExternalServiceException {
        List<Product> soldProducts = new ArrayList<>();

        // Batch processing of all active products
        tmfDataRetriever.fetchActiveProducts(50,
            product -> {
                // Keep only products where the seller has the SELLER role
                if (RelatedPartyUtils.productHasPartyWithRole(product, sellerId, Role.SELLER)) {
                    soldProducts.add(product);
                }
        });

        return soldProducts;
    }

    public Map<String, Object> getInvoice(String customerBillId) throws Exception {
        // prepare the output
        Map<String, Object> invoice = new HashMap<>();

        // retrieve the customer bill and associated acbrs
        CustomerBill cb = null;
        List<AppliedCustomerBillingRate> acbrs = null;
        if(customerBillId.startsWith("urn:ngsi-ld:revenuebill")) {
            cb = this.billService.getCustomerBillByRevenueBillId(customerBillId);
            acbrs = this.billService.getACBRsByRevenueBillId(customerBillId);
        } 
        else {
            cb = this.tmfDataRetriever.getCustomerBill(customerBillId);
            acbrs = this.tmfDataRetriever.getACBRsByCustomerBillId(customerBillId);
        }

        // bill details section =========================================
        Map<String, Object> billDetails = new HashMap<>();
        invoice.put("billDetails", billDetails);
        billDetails.put("billDate", cb.getBillDate());
        billDetails.put("billNo", cb.getBillNo());
        billDetails.put("billPeriod", cb.getBillingPeriod());
        billDetails.put("nextBillDate", cb.getNextBillDate());

        // seller info =========================================
        Map<String, Object> sellerInfo = new HashMap<>();
        invoice.put("sellerInfo", sellerInfo);
        // retrieve the sellerid
        String sellerId = RelatedPartyUtils.partyIdWithRole(cb, Role.SELLER);
        if(sellerId!=null) {
            // retrieve the organization
            Organization seller = this.tmfDataRetriever.getOrganization(sellerId);
            sellerInfo.put("dome_id", sellerId);
            sellerInfo.put("name", seller.getTradingName());
        }

        // buyer info =========================================
        Map<String, Object> buyerInfo = new HashMap<>();
        invoice.put("buyerInfo", buyerInfo);
        // retrieve the sellerid
        String buyerId = RelatedPartyUtils.partyIdWithRole(cb, Role.BUYER);
        // retrieve the organization
        if(buyerId!=null) {
            Organization buyer = this.tmfDataRetriever.getOrganization(buyerId);
            buyerInfo.put("dome_id", buyerId);
            buyerInfo.put("name", buyer.getTradingName());
        }

        // payment section =========================================
        Map<String, Object> paymentDetails = new HashMap<>();
        invoice.put("paymentDetails", paymentDetails);
        paymentDetails.put("paymentDueDate", cb.getPaymentDueDate());
        paymentDetails.put("paymentTerms", "XX days from bill date");

        // summary section =========================================
        Map<String, Object> summary = new HashMap<>();
        invoice.put("summary", summary);
        summary.put("taxExcludedAmount", cb.getTaxExcludedAmount());
        summary.put("taxIncludedAmount", cb.getTaxIncludedAmount());
        // total of taxes
        float totalTaxes = 0f;
        if(cb.getTaxItem()!=null) {
            for(TaxItem ti: cb.getTaxItem()) {
                if(ti.getTaxAmount()!= null && ti.getTaxAmount().getValue()!=null)
                    totalTaxes+=ti.getTaxAmount().getValue();
            }
            summary.put("totalTaxAmount", new Money().value(totalTaxes).unit("EUR"));
        }

        // invoice items =========================================

        Node root = new Node("");
        for(AppliedCustomerBillingRate acbr: acbrs) {
            InvoiceItem item = new InvoiceItem();
            item.description = acbr.getName();
            item.netAmount = acbr.getTaxExcludedAmount();
            item.period = acbr.getPeriodCoverage();
            item.vatPercent = acbr.getAppliedTax().get(0).getTaxRate();
            item.vat = acbr.getAppliedTax().get(0).getTaxAmount();
            root.add(item);
        }

        List<InvoiceItem> items = new ArrayList<>();
        for(Node n: root.getNodesDepthFirst()) {
            if(!n.label.isBlank()) {
//                n.content.description = new String(new char[n.getLevel()]).replace("\0", "&nbsp;&nbsp;&nbsp;&nbsp;") + n.label;
                n.content.description = n.label;
                if(!n.isLeaf() && (n.content.netAmount==null || n.content.netAmount.getValue()==null || n.content.netAmount.getValue()==0)) {
                    n.content.netAmount = null;
                    n.content.vatPercent = null;
                    n.content.vat = null;
                    n.content.period = null;
                }
                n.content.level = n.getLevel();
                items.add(n.content);
            }
        }
        invoice.put("invoiceItems", items);

        return invoice;
    }

    class InvoiceItem {
        public String description;
        public TimePeriod period;
        public Money netAmount;
        public Float vatPercent;
        public Money vat;
        public int level;
    }

    class Node {

        public String label;
        public List<Node> nodes;
        public Node parentNode;
        public InvoiceItem content;

        public Node(String label) {
            this.label = label;
            this.parentNode = null;
            this.nodes = new ArrayList<>();
            this.content = new InvoiceItem();
        }

        public void addNode(Node n) {
            this.nodes.add(n);
            n.parentNode = this;
        }

        public String getPath() {
            if(this.parentNode==null)
                return this.label;
            else
                return this.parentNode.getPath()+"/"+this.label;
        }

        public int getLevel() {
            if(this.parentNode==null)
                return 0;
            else
                return this.parentNode.getLevel()+1;
        }

        public Node getChildrenWithLabel(String label) {
            for(Node n: this.nodes) {
                if(n.label.trim().equals(label.trim()))
                    return n;
            }
            return null;
        }

        public boolean isLeaf() {
            return this.nodes==null || this.nodes.isEmpty();
        }

        public Node getOrCreateNodeWithPath(String[] path) {
            if(path.length>0) {
                Node n = this.getChildrenWithLabel(path[0]);
                if(n==null) {
                    n = new Node(path[0].trim());
                    this.addNode(n);
                }
                if(path.length>1)
                    return n.getOrCreateNodeWithPath(Arrays.copyOfRange(path, 1, path.length));
                else
                    return n.getOrCreateNodeWithPath(new String[] {});
            }
            else {
                return this;
            }
        }

        public void add(InvoiceItem item) {
            Node n = this.getOrCreateNodeWithPath(item.description.split("/"));
            n.content = item;
            if(n.content==null)
                n.content = new InvoiceItem();
        }

        public String toString() {
            return this.label.toString();
        }

        public List<Node> getNodesDepthFirst() {
            List<Node> out = new ArrayList<>();
            out.add(this);
            for(Node n:this.nodes) {
                out.addAll(n.getNodesDepthFirst());
            }
            return out;
        }

        
    }

}
