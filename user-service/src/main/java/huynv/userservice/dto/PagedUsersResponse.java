package huynv.userservice.dto;

import java.util.List;

/**
 * Represents a paginated tenant user search response.
 *
 * @param content Page content containing tenant-scoped user summaries.
 * @param page Current zero-based page index.
 * @param size Requested page size.
 * @param totalElements Total number of matching elements.
 * @param totalPages Total number of available pages.
 * @return Returns an immutable paginated user response.
 */
public record PagedUsersResponse(
        List<UserProfileResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

