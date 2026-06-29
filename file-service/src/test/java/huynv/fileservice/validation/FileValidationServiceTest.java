package huynv.fileservice.validation;

import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies filename, MIME type, extension, and checksum validation rules.
 */
class FileValidationServiceTest {

    private final FileValidationService fileValidationService = new FileValidationService(new FileServiceProperties());

    /**
     * Verifies that supported filenames yield the expected normalized extension.
     *
     * @return Performs assertions against the extracted extension.
     */
    @Test
    void extractExtensionReturnsExpectedLowercaseExtension() {
        assertThat(fileValidationService.extractExtension("Avatar.PNG")).isEqualTo("png");
    }

    /**
     * Verifies that traversal-style filenames are rejected.
     *
     * @return Performs assertions against the thrown validation exception.
     */
    @Test
    void sanitizeFilenameRejectsTraversalSequences() {
        assertThatThrownBy(() -> fileValidationService.sanitizeFilename("../secret.txt"))
                .isInstanceOf(BadRequestException.class);
    }

    /**
     * Verifies that checksum mismatches are rejected.
     *
     * @return Performs assertions against the thrown validation exception.
     */
    @Test
    void validateChecksumRejectsMismatchedValues() {
        assertThatThrownBy(() -> fileValidationService.validateChecksum("a", "b"))
                .isInstanceOf(BadRequestException.class);
    }
}

