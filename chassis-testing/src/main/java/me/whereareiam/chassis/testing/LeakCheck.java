package me.whereareiam.chassis.testing;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.time.Duration;

/**
 * Tells whether a stopped extension's class loader was collected, which proves that nothing the host keeps still
 * refers to the extension's classes.
 *
 * <pre>{@code
 * WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("greeting"));
 * host.disable("greeting");
 * assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(5)));
 * }</pre>
 */
public final class LeakCheck {
	private LeakCheck() {
	}

	/**
	 * Asks the JVM to collect garbage until the referent is gone or the time is up.
	 *
	 * @param reference a weak reference to the class loader, or to any object of the extension
	 * @param timeout   how long to try
	 * @return whether the referent was collected
	 */
	public static boolean collected(@NotNull WeakReference<?> reference, @NotNull Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (reference.get() != null && System.nanoTime() < deadline) {
			System.gc();
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();

				return reference.get() == null;
			}
		}

		return reference.get() == null;
	}
}
