package me.whereareiam.chassis.common.discovery;

import me.whereareiam.chassis.exception.DescriptorException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.Version;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorReaderTest {
	private final DescriptorReader reader = new DescriptorReader();

	@Test
	void readsEveryField() throws Exception {
		ExtensionDescriptor descriptor = read("""
				id: monitoring
				name: Monitoring
				version: 1.2.0
				main: net.example.Monitoring
				description: Reports the server.
				authors: [whereareiam]
				host: ^1
				requires:
				  gateway: ^1
				  fleet: ^1
				dependencies:
				  - id: other
				    version: ^2
				    optional: true
				platforms: [NeoForge]
				plugins: [luckperms]
				""");

		assertEquals("monitoring", descriptor.getId());
		assertEquals(Version.of(1, 2, 0), descriptor.getVersion());
		assertEquals(List.of("gateway", "fleet"), List.copyOf(descriptor.getRequires().keySet()));
		assertTrue(descriptor.getDependencies().getFirst().isOptional());
		assertEquals("^2", descriptor.getDependencies().getFirst().getVersion().toString());
		assertEquals(List.of("neoforge"), descriptor.getPlatforms());
		assertEquals(List.of("luckperms"), descriptor.getPlugins());
	}

	@Test
	void theNameDefaultsToTheIdAndAbsentListsAreEmpty() throws Exception {
		ExtensionDescriptor descriptor = read("""
				id: tiny
				version: 1
				main: net.example.Tiny
				""");

		assertEquals("tiny", descriptor.getName());
		assertTrue(descriptor.getDependencies().isEmpty());
		assertTrue(descriptor.getHost().accepts(Version.of(9, 0, 0)));
	}

	@Test
	void refusesAMissingFieldAMalformedIdAndAMalformedVersion() {
		assertEquals("The descriptor of x names no 'main'",
				assertThrows(DescriptorException.class, () -> read("id: x\nversion: 1")).getMessage());
		assertThrows(DescriptorException.class, () -> read("id: Not_Valid\nversion: 1\nmain: a.B"));
		assertThrows(DescriptorException.class, () -> read("id: ok\nversion: one\nmain: a.B"));
		assertThrows(DescriptorException.class, () -> read("id: ok\nversion: 1\nmain: a.B\nhost: ^x"));
	}

	private ExtensionDescriptor read(String text) throws DescriptorException {
		return reader.read("x.jar", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}
}
