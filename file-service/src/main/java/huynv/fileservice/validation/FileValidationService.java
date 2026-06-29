package huynv.fileservice.validation;

import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.exception.BadRequestException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates file names, extensions, MIME types, and sizes before bytes reach object storage.
 */
@Component
public class FileValidationService {

    private static final Set<String> EXECUTABLE_MIME_TYPES = Set.of(
            "application/x-dosexec",
            "application/x-executable",
            "application/x-msdownload",
            "application/x-mach-binary"
    );

    private final FileServiceProperties properties;
    private final Tika tika = new Tika();

    /**
     * Creates a validation service using the configured file upload policy.
     *
     * @param properties File-service properties containing the allowed upload policy.
     */
    public FileValidationService(FileServiceProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Validates a direct multipart upload before storage is attempted.
     *
     * @param file Multipart file uploaded by the client.
     * @param category Business category supplied by the client.
     */
    public void validateMultipartUpload(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "A non-empty file upload is required.");
        }
        normalizeCategory(category);
        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
        validateUploadPolicy(sanitizedFilename, file.getContentType(), file.getSize());
        validateMultipartContent(file, sanitizedFilename, file.getContentType());
    }

    /**
     * Performs magic-byte and archive-safety validation for a direct multipart upload.
     *
     * @param file Multipart file uploaded by the client.
     * @param sanitizedFilename Sanitized file name.
     * @param declaredContentType Content type declared by the client.
     * @return Performs a side effect by throwing when the content is unsafe or mismatched.
     */
    public void validateMultipartContent(MultipartFile file, String sanitizedFilename, String declaredContentType) {
        try (InputStream inputStream = new BufferedInputStream(file.getInputStream())) {
            inputStream.mark(16384);
            String detectedContentType = tika.detect(inputStream, sanitizedFilename);
            inputStream.reset();
            if (EXECUTABLE_MIME_TYPES.contains(detectedContentType)) {
                throw new BadRequestException("EXECUTABLE_CONTENT_BLOCKED", "Executable file content is not allowed.");
            }
            if (detectedContentType != null
                    && !detectedContentType.isBlank()
                    && declaredContentType != null
                    && !declaredContentType.isBlank()
                    && !detectedContentType.equalsIgnoreCase(declaredContentType.trim())) {
                throw new BadRequestException("CONTENT_TYPE_MISMATCH", "The detected file content type does not match the declared content type.");
            }
            if ("application/zip".equalsIgnoreCase(detectedContentType)) {
                validateZipPayload(inputStream);
            }
        } catch (IOException ex) {
            throw new BadRequestException("CONTENT_VALIDATION_FAILED", "The uploaded file content could not be inspected safely.");
        }
    }

    /**
     * Validates pre-signed upload metadata before a reservation is created.
     *
     * @param originalFilename Original file name.
     * @param contentType MIME type expected for the upload.
     * @param sizeBytes Declared upload size.
     */
    public void validateUploadPolicy(String originalFilename, String contentType, long sizeBytes) {
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String extension = extractExtension(sanitizedFilename);
        Set<String> allowedExtensions = properties.getStorage().getAllowedExtensions();
        Set<String> allowedMimeTypes = properties.getStorage().getAllowedMimeTypes();
        if (!allowedExtensions.contains(extension)) {
            throw new BadRequestException("UNSUPPORTED_FILE_EXTENSION", "The supplied file extension is not allowed.");
        }
        if (contentType == null || !allowedMimeTypes.contains(contentType.trim().toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("UNSUPPORTED_MIME_TYPE", "The supplied content type is not allowed.");
        }
        if (sizeBytes <= 0 || sizeBytes > properties.getStorage().getMaxUploadSizeBytes()) {
            throw new BadRequestException("INVALID_FILE_SIZE", "The supplied file size is invalid or exceeds the configured maximum.");
        }
    }

    /**
     * Sanitizes the client-visible file name to prevent traversal and control-character abuse.
     *
     * @param originalFilename Raw client-visible file name.
     * @return Returns a sanitized file name safe for metadata storage.
     */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("INVALID_FILENAME", "A non-empty original filename is required.");
        }
        if (originalFilename.indexOf('/') >= 0 || originalFilename.indexOf('\\') >= 0) {
            throw new BadRequestException("INVALID_FILENAME", "Path separators are not allowed in uploaded filenames.");
        }
        String sanitized = originalFilename.replace('\\', '/');
        sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1);
        sanitized = sanitized.replaceAll("[\r\n\t]", "_").trim();
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new BadRequestException("INVALID_FILENAME", "The supplied filename is invalid.");
        }
        return sanitized;
    }

    /**
     * Normalizes a business category so object keys remain partition-friendly and safe.
     *
     * @param category Raw category value.
     * @return Returns a normalized category.
     */
    public String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BadRequestException("INVALID_CATEGORY", "A non-empty category is required.");
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-");
        if (normalized.isBlank()) {
            throw new BadRequestException("INVALID_CATEGORY", "The supplied category is invalid.");
        }
        return normalized;
    }

    /**
     * Extracts and normalizes the file extension from the supplied file name.
     *
     * @param sanitizedFilename Sanitized file name.
     * @return Returns the lowercase file extension without a leading dot.
     */
    public String extractExtension(String sanitizedFilename) {
        String candidate = Objects.requireNonNull(sanitizedFilename, "sanitizedFilename");
        int lastDot = candidate.lastIndexOf('.');
        if (lastDot < 0 || lastDot == candidate.length() - 1) {
            throw new BadRequestException("INVALID_FILENAME", "The supplied filename must include a supported extension.");
        }
        return candidate.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Verifies that the provided checksum matches the expected value.
     *
     * @param expectedChecksum Expected checksum in lowercase hexadecimal form.
     * @param actualChecksum Actual checksum in lowercase hexadecimal form.
     */
    public void validateChecksum(String expectedChecksum, String actualChecksum) {
        if (!Objects.equals(expectedChecksum, actualChecksum)) {
            throw new BadRequestException("CHECKSUM_MISMATCH", "The uploaded object checksum does not match the expected checksum.");
        }
    }

    /**
     * Validates that a ZIP payload does not contain traversal entries or suspicious expansion characteristics.
     *
     * @param inputStream Input stream positioned at the ZIP payload.
     * @return Performs a side effect by throwing when the archive is unsafe.
     */
    private void validateZipPayload(InputStream inputStream) {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            int entryCount = 0;
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                String entryName = entry.getName();
                if (entryName == null || entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw new BadRequestException("ZIP_TRAVERSAL_BLOCKED", "The uploaded ZIP archive contains unsafe traversal entries.");
                }
                if (entryCount > 1000) {
                    throw new BadRequestException("ARCHIVE_BOMB_BLOCKED", "The uploaded archive contains too many entries.");
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("CONTENT_VALIDATION_FAILED", "The uploaded ZIP archive could not be inspected safely.");
        }
    }
}

