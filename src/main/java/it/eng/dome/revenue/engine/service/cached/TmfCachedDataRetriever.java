package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.utils.CacheDuration;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.annotation.PostConstruct;

@Service
public class TmfCachedDataRetriever extends TmfDataRetriever {

    public TmfCachedDataRetriever(ProductCatalogManagementApis productCatalogManagementApis,
			CustomerBillApis customerBillApis, APIPartyApis apiPartyApis, ProductInventoryApis productInventoryApis,
			AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
		
    	super(productCatalogManagementApis, customerBillApis, apiPartyApis, productInventoryApis, appliedCustomerBillRateApis);
	}

	private final Logger logger = LoggerFactory.getLogger(TmfCachedDataRetriever.class);

    @Value("${caching.tmf.enabled}")
    private Boolean TMF_CACHE_ENABLED;

    @Autowired
    CacheService cacheService;
    
	@Autowired
	CacheDuration cacheDuration;

    private Cache<String, BillingAccountRef> billingAccountCache;
    private Cache<String, CustomerBill> customerBillCache;
    private Cache<String, Organization> organizationCache;
    private Cache<String, List<Organization>> organizationsCache;
    private Cache<String, Product> productCache;
    private Cache<String, ProductOffering> productOfferingCache;
    private Cache<String, ProductOfferingPrice> productOfferingPriceCache;
    private Cache<String, List<AppliedCustomerBillingRate>> acbrCache;
    private Cache<String, List<CustomerBill>> customerBillListCache;
    private Cache<String, List<Product>> productListCache;
    private Cache<String, List<ProductOffering>> productOfferingListCache;



    @SuppressWarnings({ "unchecked" })
    @PostConstruct
    private void initCaches() {
        // --- TMF Data Retriever caches ---
        Duration billingAccountDuration = cacheDuration.getTmf().get("billingAccount");
        logger.debug("Set cache duration for 'billingAccountCache' to: {}", billingAccountDuration);
        billingAccountCache = cacheService.getOrCreateCache(
                "billingAccountCache",
                String.class,
                BillingAccountRef.class,
                billingAccountDuration
        );

        Duration customerBillDuration = cacheDuration.getTmf().get("customerBill");
        logger.debug("Set cache duration for 'customerBillCache' to: {}", customerBillDuration);
        customerBillCache = cacheService.getOrCreateCache(
                "customerBillCache",
                String.class,
                CustomerBill.class,
                customerBillDuration
        );

        Duration productDuration = cacheDuration.getTmf().get("product");
        logger.debug("Set cache duration for 'productCache' to: {}", productDuration);
        productCache = cacheService.getOrCreateCache(
                "productCache",
                String.class,
                Product.class,
                productDuration
        );

        Duration productOfferingDuration = cacheDuration.getTmf().get("productOffering");
        logger.debug("Set cache duration for 'productOfferingCache' to: {}", productOfferingDuration);
        productOfferingCache = cacheService.getOrCreateCache(
                "productOfferingCache",
                String.class,
                ProductOffering.class,
                productOfferingDuration
        );

        Duration productOfferingPriceDuration = cacheDuration.getTmf().get("productOfferingPrice");
        logger.debug("Set cache duration for 'productOfferingPriceCache' to: {}", productOfferingPriceDuration);
        productOfferingPriceCache = cacheService.getOrCreateCache(
                "productOfferingPriceCache",
                String.class,
                ProductOfferingPrice.class,
                productOfferingPriceDuration
        );

        Duration organizationDuration = cacheDuration.getTmf().get("organization");
        logger.debug("Set cache duration for 'organizationCache' to: {}", organizationDuration);
        organizationCache = cacheService.getOrCreateCache(
                "organizationCache",
                String.class,
                Organization.class,
                organizationDuration
        );

        logger.debug("Set cache duration for 'organizationsCache' to: {}", organizationDuration);
        organizationsCache = cacheService.getOrCreateCache(
                "organizationsCache",
                String.class,
                (Class<List<Organization>>)(Class<?>)List.class,
                organizationDuration
        );

        Duration acbrDuration = cacheDuration.getTmf().get("appliedCustomerBillingRate");
        logger.debug("Set cache duration for 'acbrCache' to: {}", acbrDuration);
        acbrCache = cacheService.getOrCreateCache(
                "acbrCache",
                String.class,
                (Class<List<AppliedCustomerBillingRate>>)(Class<?>)List.class,
                acbrDuration
        );

        // --- TMF List Service caches ---
        Duration productListDuration = cacheDuration.getTmf().get("list-product");
        logger.debug("Set cache duration for 'productListCache' to: {}", productListDuration);
        productListCache = cacheService.getOrCreateCache(
                "productListCache",
                String.class,
                (Class<List<Product>>)(Class<?>)List.class,
                productListDuration
        );

        Duration productOfferingListDuration = cacheDuration.getTmf().get("list-productOffering");
        logger.debug("Set cache duration for 'productOfferingListCache' to: {}", productOfferingListDuration);
        productOfferingListCache = cacheService.getOrCreateCache(
                "productOfferingListCache",
                String.class,
                (Class<List<ProductOffering>>)(Class<?>)List.class,
                productOfferingListDuration
        );

        Duration customerBillListDuration = cacheDuration.getTmf().get("list-customerBill");
        logger.debug("Set cache duration for 'customerBillListCache' to: {}", customerBillListDuration);
        customerBillListCache = cacheService.getOrCreateCache(
                "customerBillListCache",
                String.class,
                (Class<List<CustomerBill>>)(Class<?>)List.class,
                customerBillListDuration
        );
    }

    protected List<CustomerBill> retrieveCustomerBills(String participantId, Role participantRole, TimePeriod timePeriod) throws ExternalServiceException {
        String key = "all-bills-";
        if(participantId!=null)
			key  += participantId;
        if(participantRole!=null)
			key  += participantRole.getValue();
		if(timePeriod!=null)
			key  += timePeriod.toString();
        if (!TMF_CACHE_ENABLED || !this.customerBillListCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<CustomerBill> bills = super.retrieveCustomerBills(participantId, participantRole, timePeriod);
            if (bills != null) {
                this.customerBillListCache.put(key, bills);
            } else {
                logger.warn("CustomerBills not found for {}", key);
                return null;
            }
        }
        return this.customerBillListCache.get(key);        
    }


    @Override
    public List<CustomerBill> retrieveCustomerBills(String sellerId, String buyerId, TimePeriod timePeriod) throws ExternalServiceException {
        String key = "all-customer-bills";
        if(sellerId!=null)
			key  += sellerId;
        if(buyerId!=null)
			key  += buyerId;
		if(timePeriod!=null)
			key  += timePeriod.toString();
        if (!TMF_CACHE_ENABLED || !this.customerBillListCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<CustomerBill> bills = super.retrieveCustomerBills(sellerId, buyerId, timePeriod);
            if (bills != null) {
                this.customerBillListCache.put(key, bills);
            } else {
                logger.warn("CustomerBills not found for {}", key);
                return null;
            }
        }
        return this.customerBillListCache.get(key);
    }

    @Override
    public BillingAccountRef retrieveBillingAccountByProductId(String productId) throws BadTmfDataException, ExternalServiceException {
        String key = productId;
        if (!TMF_CACHE_ENABLED || !this.billingAccountCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            BillingAccountRef billingAccountRef = super.retrieveBillingAccountByProductId(productId);
            if (billingAccountRef != null) {
                this.billingAccountCache.put(key, billingAccountRef);
            } else {
                logger.warn("BillingAccountRef not found for productId {}", productId);
                return null;
            }
        }
        return this.billingAccountCache.get(key);
    }

    @Override
    public List<AppliedCustomerBillingRate> getACBRsByCustomerBillId(String customerBillId) throws BadTmfDataException, ExternalServiceException {
        String key = customerBillId;
        if (!TMF_CACHE_ENABLED || !this.acbrCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<AppliedCustomerBillingRate> acbrs = super.getACBRsByCustomerBillId(customerBillId);
            if (acbrs != null) {
                this.acbrCache.put(key, acbrs);
            } else {
                logger.warn("AppliedCustomerBillingRates not found for customerBillId {}", customerBillId);
                return null;
            }
        }
        return this.acbrCache.get(key);
    }

    @Override
    public CustomerBill getCustomerBillById(String customerBillId) throws BadTmfDataException, ExternalServiceException {
        String key = customerBillId;
        if (!TMF_CACHE_ENABLED || !this.customerBillCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            CustomerBill cb = super.getCustomerBillById(customerBillId);
            if (cb != null) {
                this.customerBillCache.put(key, cb);
            } else {
                logger.warn("CustomerBill not found for id {}", customerBillId);
                return null;
            }
        }
        return this.customerBillCache.get(key);
    }
    
    @Override
    public List<CustomerBill> getAllCustomerBills(String fields, Map<String, String> filter, int pageSize) throws ExternalServiceException {
        String key = "all-customer-bills";
        if(fields!=null)
            key += fields;
        if(filter!=null)
            key += filter.toString();
		if (!TMF_CACHE_ENABLED || !this.customerBillListCache.containsKey(key)) {
			
			logger.debug("Cache MISS for {}", key);
			List<CustomerBill> cbs = super.getAllCustomerBills(fields, filter, pageSize);
			if (cbs != null) {
				this.customerBillListCache.put(key, cbs);
			} else {
				logger.warn("CustomerBills not found");
				return null;
			}
		}
		return (List<CustomerBill>) this.customerBillListCache.get(key);
    }

    @Override
    public Product getProductById(String productId, String fields) throws BadTmfDataException, ExternalServiceException {
        String key = productId;
        // CHECKME: why fields not in the key?
        if (!TMF_CACHE_ENABLED || !this.productCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            Product prod = super.getProductById(productId, fields);
            if (prod != null) {
                this.productCache.put(key, prod);
            } else {
                logger.warn("Product not found for id {}", productId);
                return null;
            }
        }
        return this.productCache.get(key);
    }

    @Override
    public List<Product> getAllProducts(String fields, Map<String, String> filter) throws ExternalServiceException {
        String key = "all-products";
        if(fields!=null)
            key += fields;
        if(filter!=null)
            key += filter.toString();
        if (!TMF_CACHE_ENABLED || !this.productListCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<Product> prods = super.getAllProducts(fields, filter);
            if (prods != null) {
                this.productListCache.put(key, prods);
            } else {
                logger.warn("Products not found");
                return null;
            }
        }
        return this.productListCache.get(key);
    }
    
    @Override
    public List<Product> getAllSubscriptionProducts() throws ExternalServiceException {
		String key = "all-subscription-products";
		if (!TMF_CACHE_ENABLED || !this.productListCache.containsKey(key)) {
			logger.debug("Cache MISS for {}", key);
			List<Product> prods = super.getAllSubscriptionProducts();
			if (prods != null) {
				this.productListCache.put(key, prods);
			} else {
				logger.warn("Subscription Products not found");
				return null;
			}
		}
		return this.productListCache.get(key);
	}

    @Override
    public List<ProductOffering> getAllSubscriptionProductOfferings() throws ExternalServiceException {
    	String key = "all-subscription-product-offerings";
		if (!TMF_CACHE_ENABLED || !this.productOfferingListCache.containsKey(key)) {
			logger.debug("Cache MISS for {}", key);
			List<ProductOffering> pos = super.getAllSubscriptionProductOfferings();
			if (pos != null) {
				this.productOfferingListCache.put(key, pos);
			} else {
				logger.warn("Subscription ProductOfferings not found");
				return null;
			}

		}
    	return this.productOfferingListCache.get(key);
    }
    
    @Override
    public ProductOffering getProductOfferingById(String poId, String fields) throws BadTmfDataException, ExternalServiceException {
        String key = poId;
        // CHECKME: why fields not in the key?
        if (!TMF_CACHE_ENABLED || !this.productOfferingCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            ProductOffering po = super.getProductOfferingById(poId, fields);
            if (po != null) {
                this.productOfferingCache.put(key, po);
            } else {
                logger.warn("ProductOffering not found for id {}", poId);
                return null;
            }
        }
        return this.productOfferingCache.get(key);
    }

    @Override
    public List<ProductOffering> getAllProductOfferings(String fields, Map<String, String> filter) throws ExternalServiceException {
        String key = "all-product-offerings";
        if(fields!=null)
            key += fields;
        if(filter!=null)
            key += filter.toString();
        if (!TMF_CACHE_ENABLED || !this.productOfferingListCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<ProductOffering> pos = super.getAllProductOfferings(fields, filter);
            if (pos != null) {
                this.productOfferingListCache.put(key, pos);
            } else {
                logger.warn("ProductOfferings not found");
                return null;
            }
        }
        return this.productOfferingListCache.get(key);
    }

    @Override
    public ProductOfferingPrice getProductOfferingPrice(String popId, String fields) throws BadTmfDataException, ExternalServiceException {
        String key = popId;
        if (!TMF_CACHE_ENABLED || !this.productOfferingPriceCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            ProductOfferingPrice pop = super.getProductOfferingPrice(popId, fields);
            if (pop != null) {
                this.productOfferingPriceCache.put(key, pop);
            } else {
                logger.warn("ProductOfferingPrice not found for id {}", popId);
                return null;
            }
        }
        return this.productOfferingPriceCache.get(key);
    }

    @Override
    public Organization getOrganization(String organizationId) throws BadTmfDataException, ExternalServiceException{
        String key = organizationId;
        if (!TMF_CACHE_ENABLED || !this.organizationCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            Organization org = super.getOrganization(organizationId);
            if (org != null) {
                this.organizationCache.put(key, org);
            } else {
                logger.warn("Organization not found for id {}", organizationId);
                return null;
            }
        }
        return this.organizationCache.get(key);
    }

    @Override
    public List<Organization> getOrganizations() throws ExternalServiceException {
        String key = "all-organizations";
        if (!TMF_CACHE_ENABLED || !this.organizationsCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<Organization> orgs = super.getOrganizations();
            if (orgs != null) {
                this.organizationsCache.put(key, orgs);
            } else {
                return null;
            }
        }
        return this.organizationsCache.get(key);
    }

    @Override
    public List<Organization> listActiveSellersBehindFederatedMarketplace(String federatedMarketplaceId, TimePeriod timePeriod) throws BadTmfDataException, ExternalServiceException {
        String key = "active-sellers-behind-marketplace-";
        key += federatedMarketplaceId;
		if(timePeriod!=null)
			key  += timePeriod.toString();
        if (!TMF_CACHE_ENABLED || !this.organizationsCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<Organization> orgs = super.listActiveSellersBehindFederatedMarketplace(federatedMarketplaceId, timePeriod);
            if (orgs != null) {
                this.organizationsCache.put(key, orgs);
            } else {
                return null;
            }
        }
        return this.organizationsCache.get(key);
    }

    @Override
    public List<Organization> listBilledSellersBehindMarketplace(String federatedMarketplaceId, TimePeriod timePeriod) throws BadTmfDataException, ExternalServiceException {
        String key = "billed-sellers-behind-marketplace-";
        key += federatedMarketplaceId;
		if(timePeriod!=null)
			key  += timePeriod.toString();
        if (!TMF_CACHE_ENABLED || !this.organizationsCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<Organization> orgs = super.listBilledSellersBehindMarketplace(federatedMarketplaceId, timePeriod);
            if (orgs != null) {
                this.organizationsCache.put(key, orgs);
            } else {
                return null;
            }
        }
        return this.organizationsCache.get(key);
    }    

    /*
    public Organization getReferrerProvider(String referralOrganizationId) throws Exception {
        String key = referralOrganizationId;
        if (!TMF_CACHE_ENABLED || !this.organizationCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            Organization org = super.getReferrerProvider(referralOrganizationId);
            if (org != null) {
                this.organizationCache.put(key, org);
            } else {
                logger.warn("Organization not found for id {}", referralOrganizationId);
                return null;
            }
        }
        return this.organizationCache.get(key);
    }
    */

}
