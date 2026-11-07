package me.whereareiam.chassis.model;

import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The versions a dependency accepts, written as npm and Cargo write them:
 *
 * <ul>
 *     <li>{@code *} — any version;</li>
 *     <li>{@code 1.2.3} or {@code =1.2.3} — that version alone;</li>
 *     <li>{@code ^1.2} — {@code 1.2.0} up to, not including, {@code 2.0.0} (below {@code 1.0.0}: up to the next
 *     minor);</li>
 *     <li>{@code ~1.2} — {@code 1.2.0} up to, not including, {@code 1.3.0};</li>
 *     <li>{@code >=1.0}, {@code >1.0}, {@code <2}, {@code <=2} — a bound; several separated by spaces must all hold,
 *     such as {@code >=1.4 <3}.</li>
 * </ul>
 *
 * <p>A pre-release is accepted only when the range names it, so {@code ^1} does not accept {@code 2.0.0-beta.1}.</p>
 */
@EqualsAndHashCode
public final class VersionRange {
	/**
	 * The range that accepts every version.
	 */
	public static final VersionRange ANY = new VersionRange("*", List.of());

	private final String text;
	private final List<Bound> bounds;

	private VersionRange(String text, List<Bound> bounds) {
		this.text = text;
		this.bounds = bounds;
	}

	/**
	 * Reads a range.
	 *
	 * @param text the range, such as {@code ^1.2}
	 * @return the range
	 * @throws IllegalArgumentException when the text is no range
	 */
	public static @NotNull VersionRange parse(@NotNull String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty() || trimmed.equals("*")) return ANY;

		List<Bound> bounds = new ArrayList<>();
		for (String part : trimmed.split("\\s+")) bounds.addAll(bounds(part));

		return new VersionRange(trimmed, List.copyOf(bounds));
	}

	private static List<Bound> bounds(String part) {
		if (part.startsWith("^")) {
			Version from = Version.parse(part.substring(1));
			Version to = from.getMajor() > 0 ? Version.of(from.getMajor() + 1, 0, 0)
					: Version.of(0, from.getMinor() + 1, 0);

			return List.of(new Bound(Operator.AT_LEAST, from), new Bound(Operator.BELOW, to));
		}
		if (part.startsWith("~")) {
			Version from = Version.parse(part.substring(1));

			return List.of(new Bound(Operator.AT_LEAST, from), new Bound(Operator.BELOW, Version.of(from.getMajor(), from.getMinor() + 1, 0)));
		}
		for (Operator operator : Operator.values()) {
			if (operator.symbol.isEmpty() || !part.startsWith(operator.symbol)) continue;

			return List.of(new Bound(operator, Version.parse(part.substring(operator.symbol.length()))));
		}

		return List.of(new Bound(Operator.EXACTLY, Version.parse(part)));
	}

	/**
	 * Tells whether the range accepts a version.
	 *
	 * @param version the version
	 * @return whether every bound of the range holds for it
	 */
	public boolean accepts(@NotNull Version version) {
		if (version.isPreRelease() && bounds.stream().noneMatch(bound -> bound.version.isPreRelease()
				&& bound.version.getMajor() == version.getMajor()
				&& bound.version.getMinor() == version.getMinor()
				&& bound.version.getPatch() == version.getPatch())) return false;

		return bounds.stream().allMatch(bound -> bound.accepts(version));
	}

	@Override
	public String toString() {
		return text;
	}

	private enum Operator {
		// The longer symbols first, so ">=" is not read as ">".
		AT_LEAST(">="),
		AT_MOST("<="),
		ABOVE(">"),
		BELOW("<"),
		EXACTLY("=");

		private final String symbol;

		Operator(String symbol) {
			this.symbol = symbol;
		}
	}

	private record Bound(Operator operator, Version version) {
		boolean accepts(Version candidate) {
			int compared = candidate.compareTo(version);

			return switch (operator) {
				case AT_LEAST -> compared >= 0;
				case AT_MOST -> compared <= 0;
				case ABOVE -> compared > 0;
				case BELOW -> compared < 0;
				case EXACTLY -> compared == 0;
			};
		}
	}
}
