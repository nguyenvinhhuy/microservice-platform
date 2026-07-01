package huynv.event.idempotency;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcIdempotencyServiceTest {

    private static DataSource dataSource;
    private JdbcIdempotencyService service;

    /**
     * Creates the shared H2 in-memory database and initialises the {@code processed_events}
     * schema used by all test methods in this class. Runs once for the entire test class.
     *
     * @throws Exception if the data source cannot be created or the DDL statement fails.
     * @return void — sets the static {@code dataSource} field and creates the required table.
     */
    @BeforeAll
    static void createSchema() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:idempotency_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource = ds;
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS processed_events ("
                    + "event_id VARCHAR(64) NOT NULL, "
                    + "consumer_service VARCHAR(100) NOT NULL, "
                    + "processed_at TIMESTAMP NOT NULL, "
                    + "PRIMARY KEY (event_id, consumer_service))");
        }
    }

    /**
     * Instantiates a {@link JdbcIdempotencyService} for the {@code test-consumer} and truncates
     * the {@code processed_events} table before each test to guarantee a clean state.
     *
     * @throws Exception if the database connection or DELETE statement fails.
     * @return void — resets the {@code service} field and clears all rows in the test table.
     */
    @BeforeEach
    void setUp() throws Exception {
        service = new JdbcIdempotencyService(dataSource, "test-consumer");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM processed_events");
        }
    }

    /**
     * Verifies that {@link JdbcIdempotencyService#alreadyProcessed} returns {@code false} when
     * no row for the given event ID exists in the {@code processed_events} table.
     *
     * @return void — asserts that an unseen event is not reported as already processed.
     */
    @Test
    void alreadyProcessed_rowAbsent_returnsFalse() {
        assertThat(service.alreadyProcessed("evt-new")).isFalse();
    }

    /**
     * Verifies that after calling {@link JdbcIdempotencyService#markProcessed}, a row is
     * persisted in the database so that the same event ID is subsequently recognised as processed.
     *
     * @return void — asserts that the event is reported as processed after being marked.
     */
    @Test
    void alreadyProcessed_afterMarkProcessed_returnsTrue() {
        service.markProcessed("evt-001");
        assertThat(service.alreadyProcessed("evt-001")).isTrue();
    }

    /**
     * Verifies that calling {@link JdbcIdempotencyService#markProcessed} a second time with the
     * same event ID does not throw an exception, confirming idempotent insert-or-ignore behaviour.
     *
     * @return void — asserts that no exception is raised on a duplicate mark attempt.
     */
    @Test
    void markProcessed_duplicateKey_silentlyIgnored() {
        service.markProcessed("evt-dup");
        assertThatCode(() -> service.markProcessed("evt-dup")).doesNotThrowAnyException();
    }

    /**
     * Verifies that the composite primary key {@code (event_id, consumer_service)} provides
     * per-consumer isolation: an event marked by one consumer is not visible to another.
     *
     * @return void — asserts that a different consumer service sees the event as unprocessed.
     */
    @Test
    void alreadyProcessed_differentConsumerService_returnsFalse() {
        service.markProcessed("evt-001");
        JdbcIdempotencyService otherConsumer = new JdbcIdempotencyService(dataSource, "other-consumer");
        assertThat(otherConsumer.alreadyProcessed("evt-001")).isFalse();
    }

    /**
     * Verifies that constructing a {@link JdbcIdempotencyService} with a blank consumer service
     * name falls back to a default identifier rather than throwing, and that the service
     * remains fully functional for marking and querying events.
     *
     * @return void — asserts that a blank consumer name does not prevent normal operation.
     */
    @Test
    void constructor_blankConsumerService_usesUnknownConsumer() {
        JdbcIdempotencyService blankSvc = new JdbcIdempotencyService(dataSource, "   ");
        blankSvc.markProcessed("evt-blank");
        assertThat(blankSvc.alreadyProcessed("evt-blank")).isTrue();
    }

    /**
     * Verifies that constructing a {@link JdbcIdempotencyService} with a {@code null}
     * {@link javax.sql.DataSource} throws a {@link NullPointerException} immediately.
     *
     * @return void — asserts that a NullPointerException is raised for a null data source.
     */
    @Test
    void constructor_nullDataSource_throwsNullPointerException() {
        assertThatThrownBy(() -> new JdbcIdempotencyService(null, "svc"))
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that passing {@code null} as the event ID to
     * {@link JdbcIdempotencyService#alreadyProcessed} raises a {@link NullPointerException},
     * enforcing the non-null contract on the event identifier.
     *
     * @return void — asserts that a NullPointerException is thrown for a null event ID.
     */
    @Test
    void alreadyProcessed_nullEventId_throwsNullPointerException() {
        assertThatThrownBy(() -> service.alreadyProcessed(null))
            .isInstanceOf(NullPointerException.class);
    }
}
