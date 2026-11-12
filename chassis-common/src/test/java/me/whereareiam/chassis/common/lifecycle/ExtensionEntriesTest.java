package me.whereareiam.chassis.common.lifecycle;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.discovery.DescriptorReader;
import me.whereareiam.chassis.common.discovery.ExtensionDiscovery;
import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.type.ExtensionState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionEntriesTest {
	private final ExtensionEvents events = new ExtensionEvents("module", System.getLogger("test"));
	private final ExtensionEntries<ExtensionContext, Void> entries = new ExtensionEntries<>("module", events);

	@Test
	void aSecondJarOfAnInstalledIdFailsUnderItsFileNameAndATurnedOffOneIsDisabled() throws Exception {
		entries.install(found("a.jar", "greeting"), Set.of());
		entries.install(found("b.jar", "greeting"), Set.of());
		entries.install(found("c.jar", "other"), Set.of("other"));

		assertNull(entries.installed("greeting").state);
		assertEquals(ExtensionState.FAILED, entries.require("b").getState());
		assertEquals("Both a.jar and b.jar are 'greeting'; b.jar is left out", entries.require("b").getFailure().getMessage());
		assertEquals(ExtensionState.DISABLED, entries.require("other").getState());
		assertEquals("No module 'missing' is installed",
				assertThrows(ExtensionException.class, () -> entries.require("missing")).getMessage());
	}

	@Test
	void aChangeIsToldOnceAndOnlyWhenItChangesSomething() throws Exception {
		List<ExtensionEvent> told = new ArrayList<>();
		events.subscribe(told::add);
		entries.install(found("a.jar", "greeting"), Set.of());
		ExtensionEntries<ExtensionContext, Void>.Entry entry = entries.installed("greeting");

		entries.change(entry, ExtensionState.WAITING, List.of("'storage' ^2 (not installed)"), null);
		entries.change(entry, ExtensionState.WAITING, List.of("'storage' ^2 (not installed)"), null);
		entries.change(entry, ExtensionState.WAITING, List.of("'storage' ^2 (disabled)"), null);

		assertEquals(2, told.size());
		assertNull(told.getFirst().getPrevious());
		assertEquals(List.of("'storage' ^2 (disabled)"), told.getLast().getExtension().getWaitingFor());
	}

	static ExtensionDiscovery.Found<Void> found(String jar, String id) throws Exception {
		String text = "id: " + id + "\nversion: 1.0.0\nmain: a.B\n";

		return new ExtensionDiscovery.Found<>(Path.of(jar),
				new DescriptorReader().read(jar, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))), null, null);
	}
}
