package huynv.fileservice.storage;

/**
 * Defines how object storage buckets are assigned across tenants.
 */
public enum StorageBucketStrategy {
    SHARED,
    TENANT_ISOLATED
}

