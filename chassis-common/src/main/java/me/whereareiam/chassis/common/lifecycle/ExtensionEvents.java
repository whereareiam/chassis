package me.whereareiam.chassis.common.lifecycle;

import lombok.RequiredArgsConstructor;
import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Tells each change of an extension's state: to the host's listeners, and to the log where an administrator needs to
 * act, such as an extension that waits, with what for, or one that cannot start, with why.
 */
@RequiredArgsConstructor
public final class ExtensionEvents {
	// The host's word for its extensions, for log lines.
	private final @NotNull String kind;
	private final @NotNull System.Logger logger;
	private final List<Consumer<ExtensionEvent>> listeners = new CopyOnWriteArrayList<>();

	// While the host stops, every extension waits for it to start again; that is no news worth a line each.
	private boolean stopping;

	/**
	 * Adds a listener of every change.
	 *
	 * @param listener the listener; one that throws is logged, and the others are still told
	 * @return removes the listener when closed
	 */
	public @NotNull AutoCloseable subscribe(@NotNull Consumer<ExtensionEvent> listener) {
		listeners.add(listener);

		return () -> listeners.remove(listener);
	}

	/**
	 * Runs an action while the host stops, so the extensions waiting for it to start again are not logged one by one.
	 *
	 * @param action the action
	 */
	void whileStopping(@NotNull Runnable action) {
		stopping = true;
		try {
			action.run();
		} finally {
			stopping = false;
		}
	}

	/**
	 * Tells a change of an extension's state.
	 *
	 * @param extension the extension as it is now
	 * @param previous  its state before, or {@code null} when it was undecided
	 */
	void changed(@NotNull InstalledExtension extension, @Nullable ExtensionState previous) {
		log(extension, previous);

		ExtensionEvent event = ExtensionEvent.builder().extension(extension).previous(previous).build();
		for (Consumer<ExtensionEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException e) {
				logger.log(System.Logger.Level.WARNING, "A listener of the " + kind + "s failed", e);
			}
		}
	}

	// Logs an extension that waits, with what for, unless the host stops, and one that cannot start, unless it ran or
	// the launcher logged its failure already.
	private void log(InstalledExtension extension, @Nullable ExtensionState previous) {
		List<String> waitingFor = extension.getWaitingFor();
		if (extension.getState() == ExtensionState.WAITING && !waitingFor.isEmpty() && !stopping) {
			logger.log(System.Logger.Level.INFO, "The " + kind + " " + extension.getId() + " waits for " + String.join(", ", waitingFor));
			return;
		}

		Throwable failure = extension.getFailure();
		if (extension.getState() != ExtensionState.FAILED || failure == null) return;
		if (previous == ExtensionState.ENABLED || startFailure(failure)) return;

		logger.log(System.Logger.Level.WARNING, "The " + kind + " " + extension.getId() + " cannot start: " + failure.getMessage());
	}

	// The launcher logs a failure to start where it happens, with its stack; the others (unreadable jar, unfit, cycle)
	// are logged here.
	private static boolean startFailure(Throwable failure) {
		return failure instanceof ExtensionException known && known.getMessage().contains("failed to start");
	}
}
