package it.eng.dome.revenue.engine.utils;

import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.api.CustomerBillApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class to handle TMF API calls with pagination.
 * Provides generic methods to fetch all items or process results batch by batch.
 */
public class TMFApiUtils {

    /**
     * Functional interface to implement fetching a page of results from a TMF API.
     * Defines how to retrieve a page/batch of results from API.
     * <p>
     * Implementations should take care of calling the underlying API with the given
     * fields, offset, limit, and optional filters, and return the list of items for that page.
     * </p>
     * @param <T> the type of object returned by the API for each item in the batch
     */
    @FunctionalInterface
    public interface TMFFetcher<T> {
        List<T> fetch(String fields, Integer offset, Integer limit, Map<String, String> filter) throws Exception;
    }

    /**
     * Functional interface for processing a batch and deciding whether to continue.
     * Defines how to consume each batch and whether to continue fetching more or stop.
     * <p>
     * Implementations consume the provided batch of items and return a boolean indicating
     * whether the fetching process should continue:
     * - {@code true} to continue fetching the next batch,
     * - {@code false} to stop early (useful for "fetch until condition met").
     * </p>
     * @param <T> the type of objects in the batch being processed
     */
    @FunctionalInterface
    public interface BatchProcessor<T> {
        boolean consume(List<T> batch) throws Exception;
    }

    /**
     * Fetches all items with pagination and collects them into a list.
     * Uses a BatchProcessor that always returns true, so all items across all pages are collected.
     * @param fetcher TMFFetcher function to fetch a batch from the API
     * @param fields  optional fields to fetch
     * @param pageSize maximum number of items per batch
     * @param filter optional map of query filters
     * @param <T> type of objects being fetched
     * @return a list containing all items fetched
     * @throws Exception if any API call or processing fails
     */
    public static <T> List<T> fetchAll(TMFFetcher<T> fetcher, String fields, int pageSize, Map<String, String> filter) throws Exception {
        List<T> allItems = new ArrayList<>();
        fetchByBatch(fetcher, fields, pageSize, filter, batch -> {
            allItems.addAll(batch);
            return true; // always continue
        });
        return allItems;
    }

    /**
     * Fetches items batch by batch with the option to stop early.
     * The BatchProcessor can decide whether to continue fetching (return true) or stop early (return false).
     *
     * @param fetcher  fetcher TMFFetcher function to fetch a batch from the API
     * @param fields   optional fields to fetch
     * @param pageSize max items per page
     * @param filter   filter optional map of query filters
     * @param consumer function that consumes a batch and returns true to continue, false to stop
     * @param <T> type of objects being fetched
     * @throws Exception if any API call or processing fails
     */
    public static <T> void fetchByBatch(TMFFetcher<T> fetcher, String fields, int pageSize,
                                        Map<String, String> filter, BatchProcessor<T> consumer) throws Exception {
        int offset = 0;
        List<T> batch;
        boolean continueFetching;

        do {
            batch = fetcher.fetch(fields, offset, pageSize, filter);
            continueFetching = consumer.consume(batch);
            offset += pageSize;
        } while (continueFetching && !batch.isEmpty() && batch.size() == pageSize);
    }

    // ================= Specific TMF API methods =================

    // =================== Organization ====================

    /**
     * Fetches all Organization entities from TMF API.
     * Internally uses pagination to retrieve all pages and accumulates results into a single list.
     *
     * @param orgApi   the OrganizationApi client
     * @param fields   optional comma-separated list of fields to fetch (null for all)
     * @param batchSize max number of items per API call
     * @param filter   optional map of query filters (null if no filtering)
     * @return list of all Organization objects
     * @throws Exception if the API call fails
     */
    public static List<Organization> fetchAllOrganizations(OrganizationApi orgApi, String fields, int batchSize, Map<String, String> filter) throws Exception {
        return fetchAll(orgApi::listOrganization, fields, batchSize, filter);
    }

    /**
     * Fetches Organization entities in batches and processes each batch immediately using the provided consumer.
     * Allows stopping early if the consumer returns false.
     *
     * @param orgApi   the OrganizationApi client
     * @param fields   optional fields to fetch
     * @param batchSize maximum number of items per API call
     * @param filter   optional query filter map
     * @param consumer batch processor callback to consume each batch
     * @throws Exception if the API call or consumer processing fails
     */
    public static void fetchOrganizationsByBatch(OrganizationApi orgApi, String fields, int batchSize, Map<String, String> filter, BatchProcessor<Organization> consumer) throws Exception {
        fetchByBatch(orgApi::listOrganization, fields, batchSize, filter, consumer);
    }

    // =================== CustomerBill ====================

    /**
     * Fetches all CustomerBill entities from TMF API.
     * Accumulates all pages into a single list.
     *
     * @param cbApi    the CustomerBillApi client
     * @param fields   optional fields to fetch
     * @param batchSize max number of items per API call
     * @param filter   optional query filter map
     * @return list of all CustomerBill objects
     * @throws Exception if the API call fails
     */
    public static List<CustomerBill> fetchAllCustomerBills(CustomerBillApi cbApi, String fields, int batchSize, Map<String, String> filter) throws Exception {
        return fetchAll(cbApi::listCustomerBill, fields, batchSize, filter);
    }

    /**
     * Fetches CustomerBill entities in batches and processes each batch immediately using the provided consumer.
     * Can stop early if the consumer returns false.
     *
     * @param cbApi    the CustomerBillApi client
     * @param fields   optional fields to fetch
     * @param batchSize max number of items per API call
     * @param filter   optional query filter map
     * @param consumer batch processor callback
     * @throws Exception if the API call or consumer processing fails
     */
    public static void fetchCustomerBillsByBatch(CustomerBillApi cbApi, String fields, int batchSize,
                                                 Map<String, String> filter, BatchProcessor<CustomerBill> consumer) throws Exception {
        fetchByBatch(cbApi::listCustomerBill, fields, batchSize, filter, consumer);
    }

    // =================== AppliedCustomerBillingRate ====================

    /**
     * Fetches all AppliedCustomerBillingRate entities from TMF API.
     * Accumulates all pages into a single list.
     *
     * @param acbrApi  the AppliedCustomerBillingRateApi client
     * @param fields   optional fields to fetch
     * @param batchSize max number of items per API call
     * @param filter   optional query filter map
     * @return list of all AppliedCustomerBillingRate objects
     * @throws Exception if the API call fails
     */
    public static List<AppliedCustomerBillingRate> fetchAllAppliedCustomerBillingRates(AppliedCustomerBillingRateApi acbrApi, String fields, int batchSize, Map<String, String> filter) throws Exception {
        return fetchAll(acbrApi::listAppliedCustomerBillingRate, fields, batchSize, filter);
    }

    /**
     * Fetches AppliedCustomerBillingRate entities in batches and processes each batch immediately using the provided consumer.
     * Can stop early if the consumer returns false.
     *
     * @param acbrApi  the AppliedCustomerBillingRateApi client
     * @param fields   optional fields to fetch
     * @param batchSize max number of items per API call
     * @param filter   optional query filter map
     * @param consumer batch processor callback
     * @throws Exception if the API call or consumer processing fails
     */
    public static void fetchAppliedCustomerBillingRateByBatch(AppliedCustomerBillingRateApi acbrApi, String fields, int batchSize,
                                                              Map<String, String> filter, BatchProcessor<AppliedCustomerBillingRate> consumer) throws Exception {
        fetchByBatch(acbrApi::listAppliedCustomerBillingRate, fields, batchSize, filter, consumer);
    }

}