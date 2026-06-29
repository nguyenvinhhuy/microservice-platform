package huynv.fileservice.domain;

import huynv.fileservice.exception.ConflictException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Centralizes the allowed file lifecycle transitions so state changes remain strict and auditable.
 */
public final class FileLifecycleTransitionValidator {

    private static final Map<FileStatus, Set<FileStatus>> ALLOWED_TRANSITIONS = allowedTransitions();

    /**
     * Prevents instantiation of this validator utility.
     *
     * @return Throws an exception because this utility class must not be instantiated.
     */
    private FileLifecycleTransitionValidator() {
        throw new UnsupportedOperationException("FileLifecycleTransitionValidator is a utility class.");
    }

    /**
     * Validates that the requested lifecycle transition is explicitly allowed.
     *
     * @param currentStatus Current persisted lifecycle status.
     * @param targetStatus Requested lifecycle status.
     * @return Performs a side effect by throwing a conflict exception when the transition is invalid.
     */
    public static void validate(FileStatus currentStatus, FileStatus targetStatus) {
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(targetStatus, "targetStatus");
        if (currentStatus == targetStatus) {
            return;
        }
        Set<FileStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new ConflictException(
                    "INVALID_FILE_STATE_TRANSITION",
                    "The file cannot transition from " + currentStatus + " to " + targetStatus + "."
            );
        }
    }

    /**
     * Returns whether the supplied status represents a readable object state.
     *
     * @param status Lifecycle status to inspect.
     * @return Returns true only when the file is readable by normal download flows.
     */
    public static boolean isReadable(FileStatus status) {
        return status == FileStatus.AVAILABLE;
    }

    private static Map<FileStatus, Set<FileStatus>> allowedTransitions() {
        EnumMap<FileStatus, Set<FileStatus>> transitions = new EnumMap<>(FileStatus.class);
        transitions.put(FileStatus.PENDING_UPLOAD, EnumSet.of(FileStatus.PENDING_SCAN, FileStatus.UPLOAD_EXPIRED, FileStatus.DELETE_PENDING));
        transitions.put(FileStatus.PENDING_SCAN, EnumSet.of(FileStatus.AVAILABLE, FileStatus.QUARANTINED, FileStatus.SCAN_FAILED, FileStatus.DELETE_PENDING));
        transitions.put(FileStatus.AVAILABLE, EnumSet.of(FileStatus.QUARANTINED, FileStatus.DELETE_PENDING, FileStatus.ARCHIVED));
        transitions.put(FileStatus.QUARANTINED, EnumSet.of(FileStatus.DELETE_PENDING, FileStatus.ARCHIVED));
        transitions.put(FileStatus.SCAN_FAILED, EnumSet.of(FileStatus.PENDING_SCAN, FileStatus.QUARANTINED, FileStatus.DELETE_PENDING));
        transitions.put(FileStatus.UPLOAD_EXPIRED, EnumSet.of(FileStatus.DELETE_PENDING, FileStatus.DELETED));
        transitions.put(FileStatus.DELETE_PENDING, EnumSet.of(FileStatus.DELETED));
        transitions.put(FileStatus.ARCHIVED, EnumSet.of(FileStatus.DELETE_PENDING));
        transitions.put(FileStatus.DELETED, EnumSet.noneOf(FileStatus.class));
        return Map.copyOf(transitions);
    }
}

