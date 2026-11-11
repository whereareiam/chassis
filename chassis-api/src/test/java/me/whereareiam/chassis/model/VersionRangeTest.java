package me.whereareiam.chassis.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionRangeTest {
	@Test
	void aCaretAcceptsUpToTheNextMajorOrBelowOneTheNextMinor() {
		VersionRange range = VersionRange.parse("^1.2");
		assertTrue(range.accepts(Version.parse("1.2.0")));
		assertTrue(range.accepts(Version.parse("1.9.9")));
		assertFalse(range.accepts(Version.parse("1.1.9")));
		assertFalse(range.accepts(Version.parse("2.0.0")));

		VersionRange early = VersionRange.parse("^0.3");
		assertTrue(early.accepts(Version.parse("0.3.5")));
		assertFalse(early.accepts(Version.parse("0.4.0")));
	}

	@Test
	void aTildeAcceptsUpToTheNextMinor() {
		VersionRange range = VersionRange.parse("~1.2");
		assertTrue(range.accepts(Version.parse("1.2.7")));
		assertFalse(range.accepts(Version.parse("1.3.0")));
	}

	@Test
	void boundsSeparatedBySpacesMustAllHold() {
		VersionRange range = VersionRange.parse(">=1.4 <3");
		assertTrue(range.accepts(Version.parse("2.9.0")));
		assertFalse(range.accepts(Version.parse("1.3.0")));
		assertFalse(range.accepts(Version.parse("3.0.0")));
	}

	@Test
	void oneOfSeveralAlternativesMustHold() {
		VersionRange range = VersionRange.parse(">=1.16 <1.22 || ~26.1");
		assertTrue(range.accepts(Version.parse("1.21.4")));
		assertTrue(range.accepts(Version.parse("26.1.2")));
		assertFalse(range.accepts(Version.parse("1.22.0")));
		assertFalse(range.accepts(Version.parse("26.2.0")));
		assertTrue(VersionRange.parse("^1 || *").accepts(Version.parse("7.0.0")));
		assertThrows(IllegalArgumentException.class, () -> VersionRange.parse("^1 ||"));
	}

	@Test
	void aBareVersionAcceptsThatVersionAlone() {
		assertTrue(VersionRange.parse("1.2.3").accepts(Version.parse("1.2.3")));
		assertFalse(VersionRange.parse("1.2.3").accepts(Version.parse("1.2.4")));
		assertTrue(VersionRange.parse("*").accepts(Version.parse("9.0.0")));
	}

	@Test
	void aPreReleaseIsAcceptedOnlyWhenTheRangeNamesIt() {
		assertFalse(VersionRange.parse("^1").accepts(Version.parse("1.5.0-beta.1")));
		assertTrue(VersionRange.parse(">=2.0.0-beta.1").accepts(Version.parse("2.0.0-beta.2")));
		assertTrue(Version.parse("2.0.0-beta.2").compareTo(Version.parse("2.0.0-beta.10")) < 0);
		assertTrue(Version.parse("2.0.0-beta").compareTo(Version.parse("2.0.0")) < 0);
	}

	@Test
	void whatIsNoRangeIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> VersionRange.parse("^banana"));
		assertThrows(IllegalArgumentException.class, () -> Version.parse("1.x"));
	}
}
