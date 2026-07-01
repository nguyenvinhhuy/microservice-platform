package huynv.eventinfra.retry;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHeadersTest {

  /**
   * Verifies that {@link RetryHeaders#readString} decodes and returns the UTF-8 string value
   * of a header that is present in the Kafka record headers collection.
   *
   * @return void — asserts that the decoded header value equals the original UTF-8 string.
   */
  @Test
  void readString_headerPresent_returnsUtf8Value() {
    Headers headers = new RecordHeaders();
    headers.add(new RecordHeader(RetryHeaders.ATTEMPT, "3".getBytes(StandardCharsets.UTF_8)));

    assertThat(RetryHeaders.readString(headers, RetryHeaders.ATTEMPT)).isEqualTo("3");
  }

  /**
   * Verifies that {@link RetryHeaders#readString} returns {@code null} when the requested
   * header key is not present in the Kafka record headers collection.
   *
   * @return void — asserts that a missing header produces a null result.
   */
  @Test
  void readString_headerAbsent_returnsNull() {
    Headers headers = new RecordHeaders();
    assertThat(RetryHeaders.readString(headers, RetryHeaders.ATTEMPT)).isNull();
  }

  /**
   * Verifies that {@link RetryHeaders#readLong} correctly decodes an 8-byte big-endian
   * binary header into its original {@code long} value.
   *
   * @return void — asserts that the decoded long equals the original value encoded as big-endian bytes.
   */
  @Test
  void readLong_8ByteBigEndianHeader_decodesCorrectly() {
    long value = 1_700_000_000_000L;
    byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    Headers headers = new RecordHeaders();
    headers.add(new RecordHeader(RetryHeaders.RETRY_DUE_AT_MS, bytes));

    assertThat(RetryHeaders.readLong(headers, RetryHeaders.RETRY_DUE_AT_MS)).isEqualTo(value);
  }

  /**
   * Verifies that {@link RetryHeaders#readLong} can parse a header stored as a UTF-8 decimal
   * string, supporting both binary and text encodings of long-valued headers.
   *
   * @return void — asserts that the UTF-8 string representation of 42 is parsed to the long 42.
   */
  @Test
  void readLong_utf8StringHeader_parsesAsLong() {
    Headers headers = new RecordHeaders();
    headers.add(new RecordHeader(RetryHeaders.FIRST_SEEN_AT_MS, "42".getBytes(StandardCharsets.UTF_8)));

    assertThat(RetryHeaders.readLong(headers, RetryHeaders.FIRST_SEEN_AT_MS)).isEqualTo(42L);
  }

  /**
   * Verifies that {@link RetryHeaders#readLong} returns {@code null} when the requested
   * header key is not present in the Kafka record headers collection.
   *
   * @return void — asserts that a missing long header produces a null result.
   */
  @Test
  void readLong_headerAbsent_returnsNull() {
    Headers headers = new RecordHeaders();
    assertThat(RetryHeaders.readLong(headers, RetryHeaders.FIRST_SEEN_AT_MS)).isNull();
  }

  /**
   * Verifies that encoding a {@code long} with {@link RetryHeaders#toLongBytes} and then
   * decoding it with {@link RetryHeaders#readLong} recovers the original value without loss.
   *
   * @return void — asserts that the round-trip encode/decode produces the original long value.
   */
  @Test
  void toLongBytes_andReadLong_roundTrip() {
    long original = 9_876_543_210L;
    byte[] encoded = RetryHeaders.toLongBytes(original);
    Headers headers = new RecordHeaders();
    headers.add(new RecordHeader("x-test", encoded));

    assertThat(RetryHeaders.readLong(headers, "x-test")).isEqualTo(original);
  }

  /**
   * Verifies that {@link RetryHeaders#toUtf8} returns {@code null} when given a null input,
   * allowing callers to safely skip adding a header when the value is absent.
   *
   * @return void — asserts that a null string input produces a null byte array.
   */
  @Test
  void toUtf8_nullValue_returnsNull() {
    assertThat(RetryHeaders.toUtf8(null)).isNull();
  }

  /**
   * Verifies that {@link RetryHeaders#toUtf8} encodes a non-null string into UTF-8 bytes
   * that can be decoded back to the original string value.
   *
   * @return void — asserts that the UTF-8 encoded bytes represent the original string.
   */
  @Test
  void toUtf8_nonNullValue_returnsUtf8Bytes() {
    byte[] result = RetryHeaders.toUtf8("hello");
    assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("hello");
  }

  /**
   * Verifies that passing {@code null} as the headers argument to
   * {@link RetryHeaders#readString} throws a {@link NullPointerException}, enforcing the
   * non-null precondition on the headers parameter.
   *
   * @return void — asserts that a NullPointerException is raised for a null headers argument.
   */
  @Test
  void readString_nullHeaders_throwsNullPointerException() {
    assertThatThrownBy(() -> RetryHeaders.readString(null, "key"))
        .isInstanceOf(NullPointerException.class);
  }
}
