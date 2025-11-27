package it.eng.dome.revenue.engine.model;

public enum ComputeMetric {
    BILLS_NO_TAXES("bills-no-taxes"),
    REFERRED_PROVIDERS_NUMBER("referred-providers-number"),
    REFERRED_PROVIDERS_TRANSACTION_VOLUME("referred-providers-transaction-volume"),
    REFERRED_PROVIDER_MAX_TRANSACTION_VOLUME("referred-provider-max-transaction-volume"),
    PUBLISHED_PRODUCT_OFFERINGS("published-product-offerings"),
    PUBLISHED_SELFSERVICE_PRODUCT_OFFERINGS("published-selfservice-product-offerings");

    private final String key;

    ComputeMetric(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    // method to get enum from key
    public static ComputeMetric fromKey(String key) {
        for (ComputeMetric m : values()) {
            if (m.getKey().equalsIgnoreCase(key)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown metric key: " + key);
    }
}

