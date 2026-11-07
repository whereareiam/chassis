package me.whereareiam.chassis.common;

import java.lang.ref.WeakReference;

/**
 * Where the tests' extensions leave weak references to what the tests check is collected.
 */
public final class Witness {
	public static volatile WeakReference<ClassLoader> failingLoader = new WeakReference<>(null);

	private Witness() {
	}
}
