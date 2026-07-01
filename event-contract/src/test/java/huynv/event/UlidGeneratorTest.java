package huynv.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UlidGeneratorTest {

  private static final String CROCKFORD_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  /**
   * Verifies that {@link UlidGenerator#nextUlid()} returns a string of exactly 26 characters,
   * as required by the ULID specification.
   *
   * @return void — asserts the generated ULID has a length of 26.
   */
  @Test
  void nextUlid_returns26CharacterString() {
    String ulid = UlidGenerator.nextUlid();
    assertThat(ulid).hasSize(26);
  }

  /**
   * Verifies that every character of a generated ULID belongs to the Crockford Base32 alphabet,
   * which excludes ambiguous characters such as I, L, O, and U.
   *
   * @return void — asserts each character in the ULID is a valid Crockford Base32 symbol.
   */
  @Test
  void nextUlid_onlyContainsCrockfordBase32Characters() {
    String ulid = UlidGenerator.nextUlid();
    Set<Character> allowed = new java.util.HashSet<>();
    for (char c : CROCKFORD_CHARS.toCharArray()) {
      allowed.add(c);
    }
    for (char c : ulid.toCharArray()) {
      assertThat(allowed).contains(c);
    }
  }

  /**
   * Verifies that {@link UlidGenerator#nextUlid(Instant)} produces a 26-character ULID
   * when a fixed timestamp is supplied as the time component.
   *
   * @return void — asserts the timestamp-seeded ULID has a length of 26.
   */
  @Test
  void nextUlid_withInstant_returns26CharacterString() {
    String ulid = UlidGenerator.nextUlid(Instant.parse("2024-06-01T00:00:00Z"));
    assertThat(ulid).hasSize(26);
  }

  /**
   * Verifies that successive calls to {@link UlidGenerator#nextUlid()} produce distinct values,
   * confirming that the random entropy component prevents collisions in practice.
   *
   * @return void — asserts that two consecutively generated ULIDs are not equal.
   */
  @Test
  void nextUlid_successiveCalls_returnDistinctValues() {
    String first = UlidGenerator.nextUlid();
    String second = UlidGenerator.nextUlid();
    // entropy component makes collision statistically impossible
    assertThat(first).isNotEqualTo(second);
  }
}
