package org.example.attache.library;

/**
 * A class of a library jar that only the extension's Attache descriptor declares.
 */
public final class Letters {
	public static String shout(String text) {
		return text.toUpperCase(java.util.Locale.ROOT);
	}

	private Letters() {
	}
}
