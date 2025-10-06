package it.eng.dome.revenue.engine.service.cached;

import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class TmfCachedDataRetriever extends TmfDataRetriever {

    private final Logger logger = LoggerFactory.getLogger(TmfCachedDataRetriever.class);

    @Value("${caching.tmf.enabled}")
    private Boolean TMF_CACHE_ENABLED;

    @Autowired
    CacheService cacheService;

    private Cache<String, List<AppliedCustomerBillingRate>> acbrCache;
    private Cache<String, BillingAccountRef> billingAccountCache;
    private Cache<String, CustomerBill> customerBillCache;
    private Cache<String, Product> productCache;
    private Cache<String, List<Product>> productListCache;
    private Cache<String, ProductOffering> productOfferingCache;
    private Cache<String, List<ProductOffering>> productOfferingListCache;
    private Cache<String, ProductOfferingPrice> productOfferingPriceCache;
    private Cache<String, Organization> organizationCache;

    public TmfCachedDataRetriever() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        this.acbrCache = this.cacheService.getOrCreateCache(
                "acbrCache",
                String.class,
                (Class<List<AppliedCustomerBillingRate>>)(Class<?>)List.class,
                Duration.ofHours(1));

        this.billingAccountCache = this.cacheService.getOrCreateCache(
                "billingAccountCache",
                String.class,
                BillingAccountRef.class,
                Duration.ofHours(1));

        this.customerBillCache = this.cacheService.getOrCreateCache(
                "customerBillCache",
                String.class,
                CustomerBill.class,
                Duration.ofHours(1));

        this.productCache = this.cacheService.getOrCreateCache(
                "productCache",
                String.class,
                Product.class,
                Duration.ofHours(1));

        this.productListCache = this.cacheService.getOrCreateCache(
                "productListCache",
                String.class,
                (Class<List<Product>>)(Class<?>)List.class,
                Duration.ofMinutes(30));

        this.productOfferingCache = this.cacheService.getOrCreateCache(
                "productOfferingCache",
                String.class,
                ProductOffering.class,
                Duration.ofHours(1));

        this.productOfferingListCache = this.cacheService.getOrCreateCache(
                "productOfferingListCache",
                String.class,
                (Class<List<ProductOffering>>)(Class<?>)List.class,
                Duration.ofMinutes(30));

        this.productOfferingPriceCache = this.cacheService.getOrCreateCache(
                "productOfferingPriceCache",
                String.class,
                ProductOfferingPrice.class,
                Duration.ofHours(1));

        this.organizationCache = this.cacheService.getOrCreateCache(
                "organizationCache",
                String.class,
                Organization.class,
                Duration.ofHours(1));
    }

    @Override
    public List<AppliedCustomerBillingRate> retrieveBills(String sellerId, TimePeriod timePeriod, Boolean isBilled) throws Exception {
        String key = "bills:" + sellerId + ":" + timePeriod.toString() + ":" + isBilled;
        if (!TMF_CACHE_ENABLED || !this.acbrCache.containsKey(key)) {
            logger.debug("Cache MISS for {}", key);
            List<AppliedCustomerBillingRate> acbrs = super.retrieveBills(sellerId, timePeriod, isBilled);
            if (acbrs != null) {
                this.acbrCache.put(key, acbrs);
            } else {
                logger.warn("AppliedCustomerBillingRates not found for {}", key);
                return null;
            }
        }
        return this.acbrCache.get(key);
    }

    @Override
    public BillingAccountRef retrieveBillingAccountByProductId(String productId) {
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
    public List<AppliedCustomerBillingRate> getACBRsByCustomerBillId(String customerBillId) {
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
    public CustomerBill getCustomerBillById(String customerBillId) {
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
    public Product getProductById(String productId, String fields) {
        String key = productId;
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
    public List<Product> getAllProducts(String fields, Map<String, String> filter) {
        String key = "all-products";
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
    public ProductOffering getProductOfferingById(String poId, String fields) {
        String key = poId;
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
    public List<ProductOffering> getAllProductOfferings(String fields, Map<String, String> filter) {
        String key = "all-product-offerings";
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
    public ProductOfferingPrice getProductOfferingPrice(String popId, String fields) {
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
    public Organization getOrganization(String organizationId) throws ApiException{
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
