package huynv.fileservice.storage;

import huynv.fileservice.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates tenant-safe object keys that are stable, path-traversal-safe, and partition-friendly.
 */
@Component
public class StorageObjectKeyFactory {

    private final Clock clock;

    /**
     * Creates an object-key factory using the system UTC clock.
     *
     * @return Initializes the object-key factory.
     */
    public StorageObjectKeyFactory() {
        this(Clock.systemUTC());
    }

    /**
     * Creates an object-key factory with an explicit clock for deterministic testing.
     *
     * @param clock Clock used to derive the date hierarchy.
     * @return Initializes the object-key factory.
     */
    public StorageObjectKeyFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Builds a storage object key using tenant, category, date hierarchy, and a randomized suffix.
     *
     * @param tenantId Tenant identifier.
     * @param category Business category.
     * @param fileId File identifier.
     * @param extension Sanitized file extension without a leading dot.
     * @return Returns a generated object key safe for S3-compatible storage.
     */
    public String createObjectKey(UUID tenantId, String category, UUID fileId, String extension) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(fileId, "fileId");
        String normalizedCategory = sanitizePathSegment(category);
        String normalizedExtension = sanitizeExtension(extension);
        LocalDate date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return tenantId
                + "/" + normalizedCategory
                + "/" + date.getYear()
                + "/" + String.format("%02d", date.getMonthValue())
                + "/" + String.format("%02d", date.getDayOfMonth())
                + "/" + fileId + "-" + UUID.randomUUID() + "." + normalizedExtension;
    }

    /**
     * Sanitizes a path segment so object keys cannot escape the intended prefix hierarchy.
     *
     * @param rawValue Raw segment value.
     * @return Returns a sanitized path segment safe for object-key composition.
     */
    public String sanitizePathSegment(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BadRequestException("INVALID_PATH_SEGMENT", "A non-empty path segment is required.");
        }
        String sanitized = rawValue.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            throw new BadRequestException("INVALID_PATH_SEGMENT", "The supplied path segment is invalid.");
        }
        return sanitized;
    }

    /**
     * Sanitizes a file extension so object keys cannot contain dangerous traversal tokens.
     *
     * @param extension Raw file extension.
     * @return Returns a sanitized lowercase extension.
     */
    public String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BadRequestException("INVALID_EXTENSION", "A supported file extension is required.");
        }
        String sanitized = extension.trim().toLowerCase().replace(".", "").replaceAll("[^a-z0-9]", "");
        if (sanitized.isBlank()) {
            throw new BadRequestException("INVALID_EXTENSION", "A supported file extension is required.");
        }
        return sanitized;
    }
}

