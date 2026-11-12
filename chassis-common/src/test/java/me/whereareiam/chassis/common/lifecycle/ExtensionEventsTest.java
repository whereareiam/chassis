package me.whereareiam.chassis.common.lifecycle;

import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.type.ExtensionState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionEventsTest {
	private final List<String> logged = new ArrayList<>();
	private final ExtensionEvents events = new ExtensionEvents("module", new Lines(logged));

	@Test
	void aFailingListenerIsLoggedAndTheOthersAreStillTold() {
		List<ExtensionEvent> told = new ArrayList<>();
		events.subscribe(event -> {
			throw new IllegalStateException("broken listener");
		});
		events.subscribe(told::add);

		events.changed(extension("greeting", ExtensionState.WAITING, List.of("'storage' ^2 (not installed)"), null), null);

		assertEquals(1, told.size());
		assertEquals(List.of(
				"INFO The module greeting waits for 'storage' ^2 (not installed)",
				"WARNING A listener of the modules failed"), logged);
	}

	@Test
	void waitingForTheHostToStartAgainIsNotLoggedAndAFailureToStartIsLeftToTheLauncher() {
		events.whileStopping(() -> events.changed(extension("greeting", ExtensionState.WAITING, List.of("the modules' host to start"),
				null), ExtensionState.ENABLED));
		events.changed(extension("other", ExtensionState.FAILED, List.of(),
				new ExtensionException("other", "'other' failed to start: boom")), null);
		events.changed(extension("greeting", ExtensionState.FAILED, List.of(),
				new ExtensionException("greeting", "It runs on paper, not on test")), null);

		assertEquals(List.of("WARNING The module greeting cannot start: It runs on paper, not on test"), logged);
	}

	private static InstalledExtension extension(String id, ExtensionState state, List<String> waitingFor, Throwable failure) {
		return InstalledExtension.builder()
				.id(id)
				.jar(Path.of(id + ".jar"))
				.state(state)
				.waitingFor(waitingFor)
				.failure(failure)
				.build();
	}

	// Keeps each line as its level and message.
	private record Lines(List<String> lines) implements System.Logger {
		@Override
		public String getName() {
			return "test";
		}

		@Override
		public boolean isLoggable(Level level) {
			return true;
		}

		@Override
		public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
			lines.add(level + " " + message);
		}

		@Override
		public void log(Level level, ResourceBundle bundle, String format, Object... params) {
			lines.add(level + " " + format);
		}

		@Override
		public void log(Level level, Supplier<String> message) {
			lines.add(level + " " + message.get());
		}
	}
}
