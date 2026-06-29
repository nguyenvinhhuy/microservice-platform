package huynv.fileservice.event;

import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.MalwareScanStatus;
import huynv.fileservice.exception.StorageOperationException;
import huynv.fileservice.repository.ChecksumBlacklistRepository;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Implements a ClamAV-compatible malware scanner using the daemon INSTREAM protocol with checksum blacklist short-circuiting.
 */
@Component
public class ClamAvScanner implements MalwareScanner {

    private final FileServiceProperties properties;
    private final ChecksumBlacklistRepository checksumBlacklistRepository;

    /**
     * Creates a ClamAV scanner backed by file-service scan settings and checksum blacklist persistence.
     *
     * @param properties File-service properties containing ClamAV connection settings.
     * @param checksumBlacklistRepository Repository used to short-circuit known-malicious checksums.
     * @return Initializes the ClamAV scanner.
     */
    public ClamAvScanner(FileServiceProperties properties, ChecksumBlacklistRepository checksumBlacklistRepository) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.checksumBlacklistRepository = Objects.requireNonNull(checksumBlacklistRepository, "checksumBlacklistRepository");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScanVerdict scan(FileRecord fileRecord, InputStream inputStream) {
        Objects.requireNonNull(fileRecord, "fileRecord");
        Objects.requireNonNull(inputStream, "inputStream");
        if (checksumBlacklistRepository.findByChecksumSha256AndExpiresAtAfterOrExpiresAtIsNull(fileRecord.getChecksumSha256(), Instant.now()).isPresent()) {
            return new ScanVerdict(MalwareScanStatus.INFECTED, "The checksum is present in the malicious checksum blacklist.", false, true);
        }
        if ("MOCK".equalsIgnoreCase(properties.getScan().getMode())) {
            return new ScanVerdict(MalwareScanStatus.CLEAN, "The mock scanner accepted the object.", false, false);
        }
        try (Socket socket = new Socket()) {
            int timeoutMillis = Math.toIntExact(properties.getScan().getTimeout().toMillis());
            socket.connect(new InetSocketAddress(properties.getScan().getHost(), properties.getScan().getPort()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            socket.getOutputStream().write("zINSTREAM\u0000".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bufferedInputStream.read(buffer)) >= 0) {
                    if (bytesRead == 0) {
                        continue;
                    }
                    socket.getOutputStream().write(ByteBuffer.allocate(4).putInt(bytesRead).array());
                    socket.getOutputStream().write(buffer, 0, bytesRead);
                }
                socket.getOutputStream().write(new byte[] {0, 0, 0, 0});
                socket.getOutputStream().flush();
            }
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (response.endsWith("FOUND")) {
                return new ScanVerdict(MalwareScanStatus.INFECTED, response, false, false);
            }
            if (response.endsWith("OK")) {
                return new ScanVerdict(MalwareScanStatus.CLEAN, response, false, false);
            }
            return new ScanVerdict(MalwareScanStatus.FAILED, response.isBlank() ? "The scanner returned an empty response." : response, false, false);
        } catch (Exception ex) {
            throw new StorageOperationException("SCAN_EXECUTION_FAILED", "Failed to scan the object with ClamAV. Cause: " + ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String scannerName() {
        return "clamav";
    }
}

