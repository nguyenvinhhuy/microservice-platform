package huynv.event.idempotency;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Implements consumer-side idempotency using plain JDBC against the processed_events table contract.
 */
public final class JdbcIdempotencyService implements IdempotencyService {

    private final DataSource dataSource;
    private final String consumerService;

    /**
     * Creates a JDBC-backed idempotency service using the processed_events table.
     *
     * @param dataSource DataSource used to query and insert processed markers.
     * @param consumerService Consumer service name recorded in processed_events rows.
     * @return Initializes a JDBC idempotency service instance.
     */
    public JdbcIdempotencyService(DataSource dataSource, String consumerService) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.consumerService = consumerService == null || consumerService.isBlank() ? "unknown-consumer" : consumerService;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM processed_events WHERE event_id = ? AND consumer_service = ?"
             )) {
            statement.setString(1, eventId);
            statement.setString(2, consumerService);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to check processed marker for consumerService=" + consumerService + ".", ex);
        }
    }

    @Override
    public void markProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO processed_events (event_id, consumer_service, processed_at) VALUES (?, ?, ?)"
             )) {
            statement.setString(1, eventId);
            statement.setString(2, consumerService);
            statement.setTimestamp(3, Timestamp.from(OffsetDateTime.now().toInstant()));
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (isDuplicateKey(ex)) {
                return;
            }
            throw new IllegalStateException("Failed to persist processed marker for consumerService=" + consumerService + ".", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist processed marker for consumerService=" + consumerService + ".", ex);
        }
    }

    /**
     * Determines whether a SQL exception represents a duplicate key insert attempt.
     *
     * @param ex SQL exception to inspect.
     * @return Returns true when the exception indicates a unique constraint violation.
     */
    private static boolean isDuplicateKey(SQLException ex) {
        if (ex == null) {
            return false;
        }
        String sqlState = ex.getSQLState();
        if ("23505".equals(sqlState)) {
            return true;
        }
        SQLException next = ex.getNextException();
        if (next != null && "23505".equals(next.getSQLState())) {
            return true;
        }
        return false;
    }
}

