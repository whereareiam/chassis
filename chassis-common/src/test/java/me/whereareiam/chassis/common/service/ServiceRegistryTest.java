package me.whereareiam.chassis.common.service;

import me.whereareiam.chassis.service.ServiceKey;
import me.whereareiam.chassis.service.ServiceRegistration;
import me.whereareiam.chassis.service.Services;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRegistryTest {
	private static final ServiceKey<CharSequence> KEY = ServiceKey.of(CharSequence.class);

	private final ServiceRegistry registry = new ServiceRegistry(error -> {
		throw new AssertionError(error);
	});

	@Test
	void theHighestPriorityAnswersAndTheFirstAmongEquals() {
		Services host = registry.as("host");
		host.register(KEY, "first");
		host.register(KEY, "second");
		host.register(KEY, "urgent", 5);

		assertEquals("urgent", host.find(KEY).orElseThrow());
		assertEquals(List.of("urgent", "first", "second"), host.all(KEY));
	}

	@Test
	void removingAnOwnerTakesItsServicesAndWatchesAndTellsTheOtherWatchers() throws Exception {
		Services host = registry.as("host");
		Services module = registry.as("module");
		List<String> seen = new ArrayList<>();
		host.watch(KEY, event -> seen.add((event.isRegistered() ? "+" : "-") + event.getService() + "@" + event.getOwner()));
		module.watch(KEY, event -> seen.add("module saw " + event.getService()));
		module.register(KEY, "hello");

		registry.removeOwner("module");
		host.register(KEY, "after");

		assertEquals(List.of("+hello@module", "module saw hello", "-hello@module", "+after@host"), seen);
		assertFalse(registry.holdsAnything("module"));
		assertTrue(host.find(KEY).isPresent());
	}

	@Test
	void closingARegistrationRemovesTheServiceOnce() {
		Services host = registry.as("host");
		List<Boolean> seen = new ArrayList<>();
		host.watch(KEY, event -> seen.add(event.isRegistered()));
		ServiceRegistration registration = host.register(KEY, "x");

		registration.close();
		registration.close();

		assertEquals(List.of(true, false), seen);
		assertTrue(host.find(KEY).isEmpty());
	}

	@Test
	void aWatchStartsWithWhatIsRegisteredAlready() {
		Services host = registry.as("host");
		host.register(KEY, "present");
		List<CharSequence> seen = new ArrayList<>();

		host.watch(KEY, event -> seen.add(event.getService()));

		assertEquals(List.of("present"), seen);
	}
}
