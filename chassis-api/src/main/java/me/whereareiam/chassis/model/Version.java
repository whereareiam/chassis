package me.whereareiam.chassis.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version of an extension or a host, {@code major.minor.patch} with an optional pre-release after {@code -}, as
 * semantic versioning has it. Missing parts read as {@code 0}, so {@code 2} is {@code 2.0.0}. A pre-release orders
 * before its release; build metadata after {@code +} is kept but not compared.
 */
@Getter
@EqualsAndHashCode(exclude = "build")
public final class Version implements Comparable<Version> {
	private static final Pattern FORMAT =
			Pattern.compile("v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?");

	private final int major;
	private final int minor;
	private final int patch;
	private final @NotNull String preRelease;
	private final @NotNull String build;

	private Version(int major, int minor, int patch, @NotNull String preRelease, @NotNull String build) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.preRelease = preRelease;
		this.build = build;
	}

	/**
	 * Makes a release version.
	 *
	 * @param major the major version
	 * @param minor the minor version
	 * @param patch the patch version
	 * @return the version
	 */
	public static @NotNull Version of(int major, int minor, int patch) {
		if (major < 0 || minor < 0 || patch < 0) throw new IllegalArgumentException("A version has no negative parts");

		return new Version(major, minor, patch, "", "");
	}

	/**
	 * Reads a version, such as {@code 1.2.3}, {@code 1.2}, {@code 2.0.0-beta.1} or {@code v1.0.0}.
	 *
	 * @param text the version
	 * @return the version
	 * @throws IllegalArgumentException when the text is no version
	 */
	public static @NotNull Version parse(@NotNull String text) {
		Matcher matcher = FORMAT.matcher(text.trim());
		if (!matcher.matches()) throw new IllegalArgumentException("Not a version: '" + text + "'");

		return new Version(
				Integer.parseInt(matcher.group(1)),
				matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
				matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)),
				matcher.group(4) == null ? "" : matcher.group(4),
				matcher.group(5) == null ? "" : matcher.group(5)
		);
	}

	/**
	 * Tells whether this is a pre-release, such as {@code 2.0.0-beta.1}.
	 *
	 * @return whether the version has a pre-release part
	 */
	public boolean isPreRelease() {
		return !preRelease.isEmpty();
	}

	@Override
	public int compareTo(@NotNull Version other) {
		int compared = Integer.compare(major, other.major);
		if (compared == 0) compared = Integer.compare(minor, other.minor);
		if (compared == 0) compared = Integer.compare(patch, other.patch);
		if (compared != 0) return compared;
		if (preRelease.equals(other.preRelease)) return 0;
		if (preRelease.isEmpty()) return 1;
		if (other.preRelease.isEmpty()) return -1;

		return comparePreRelease(preRelease, other.preRelease);
	}

	private static int comparePreRelease(String left, String right) {
		String[] a = left.split("\\.");
		String[] b = right.split("\\.");
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			boolean numericA = a[i].chars().allMatch(Character::isDigit);
			boolean numericB = b[i].chars().allMatch(Character::isDigit);
			int compared;
			if (numericA && numericB) compared = Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i]));
			else if (numericA) compared = -1;
			else if (numericB) compared = 1;
			else compared = a[i].compareTo(b[i]);
			if (compared != 0) return compared;
		}

		return Integer.compare(a.length, b.length);
	}

	@Override
	public String toString() {
		String text = major + "." + minor + "." + patch;
		if (!preRelease.isEmpty()) text += "-" + preRelease;
		if (!build.isEmpty()) text += "+" + build;

		return text;
	}
}
