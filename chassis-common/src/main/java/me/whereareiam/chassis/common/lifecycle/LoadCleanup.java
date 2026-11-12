package me.whereareiam.chassis.common.lifecycle;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * Closes what one load of an extension made, whatever each part throws, and keeps a failure free of the extension's
 * classes, for the {@link ExtensionLauncher} when a start fails and when an extension stops.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LoadCleanup {
	// A copy of a failure that refers to no class of an extension, kept while the extension is failed. A throwable
	// keeps the classes of the frames it was made in, and may be of a type of the extension's own jar, so the original
	// would keep the extension's class loader until it is enabled again. The copy keeps the type's name, the message
	// and the stack trace as text.
	static Throwable detached(Throwable failure) {
		String message = failure.getMessage() == null ? "" : ": " + failure.getMessage();
		Throwable copy = new RuntimeException(failure.getClass().getName() + message);
		copy.setStackTrace(failure.getStackTrace());

		if (failure.getCause() != null && failure.getCause() != failure) copy.initCause(detached(failure.getCause()));
		for (Throwable suppressed : failure.getSuppressed()) copy.addSuppressed(detached(suppressed));

		return copy;
	}

	// Closes each resource that is there, in order, whatever the others threw. Each failure is added to the given
	// one, or becomes it when there is none; the result is the failure, or null.
	static @Nullable Throwable closeAll(@Nullable Throwable failure, @Nullable AutoCloseable... resources) {
		for (AutoCloseable resource : resources) {
			if (resource == null) continue;

			try {
				resource.close();
			} catch (Exception e) {
				failure = joined(failure, e);
			}
		}

		return failure;
	}

	// The context as a resource: its close reports a failure instead of throwing it.
	static @Nullable AutoCloseable closing(@Nullable LoadedContext context) {
		if (context == null) return null;

		return () -> {
			Exception failure = context.close();
			if (failure != null) throw failure;
		};
	}

	static Throwable joined(@Nullable Throwable failure, Throwable next) {
		if (failure == null) return next;

		failure.addSuppressed(next);

		return failure;
	}
}
