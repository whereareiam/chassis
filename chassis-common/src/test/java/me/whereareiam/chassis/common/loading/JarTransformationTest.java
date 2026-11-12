package me.whereareiam.chassis.common.loading;

import me.whereareiam.chassis.ExtensionTransformer;
import me.whereareiam.chassis.common.discovery.DescriptorReader;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.testing.ExtensionJar;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarTransformationTest {
	@TempDir
	Path folder;

	@Test
	void withoutTransformersTheInstalledJarLoads() throws Exception {
		Path jar = jar("greeting");

		assertSame(jar, new JarTransformation(List.of(), folder.resolve("transformed")).transform(descriptor("greeting"), jar));
		assertFalse(Files.exists(folder.resolve("transformed")));
	}

	@Test
	void transformersRunInOrderEachOnTheResultOfThePrevious() throws Exception {
		Path jar = jar("greeting");
		Marking first = new Marking("first");
		Marking second = new Marking("second");

		Path loaded = new JarTransformation(List.of(first, second), folder.resolve("transformed")).transform(descriptor("greeting"), jar);

		assertEquals(folder.resolve("transformed/greeting-first-1-second-1.jar"), loaded);
		assertEquals("first,second", Marking.marks(loaded));
		assertEquals(List.of("greeting.jar"), first.transformed);
		assertEquals(List.of("greeting-first-1.jar"), second.transformed);
	}

	@Test
	void aResultIsReusedUntilItsJarOrTheTransformersVersionChanges() throws Exception {
		Path jar = jar("greeting");
		Marking marking = new Marking("marking");
		JarTransformation transformation = new JarTransformation(List.of(marking), folder.resolve("transformed"));

		transformation.transform(descriptor("greeting"), jar);
		new JarTransformation(List.of(marking), folder.resolve("transformed")).transform(descriptor("greeting"), jar);
		assertEquals(1, marking.transformed.size());

		ExtensionJar.of("test-module.yml", "").with(Marking.class).write(jar);
		transformation.transform(descriptor("greeting"), jar);
		assertEquals(2, marking.transformed.size());

		Marking newer = new Marking("marking", "2");
		Path loaded = new JarTransformation(List.of(newer), folder.resolve("transformed")).transform(descriptor("greeting"), jar);
		assertEquals(1, newer.transformed.size());
		assertEquals(folder.resolve("transformed/greeting-marking-2.jar"), loaded);
	}

	@Test
	void aJarATransformerLeavesAsItIsLoadsAsItIsAndIsNotReadAgain() throws Exception {
		Path jar = jar("greeting");
		Marking unchanged = new Marking("unchanged") {
			@Override
			public @NotNull Path transform(@NotNull ExtensionDescriptor extension, @NotNull Path input, @NotNull Path output) {
				transformed.add(input.getFileName().toString());
				return input;
			}
		};
		JarTransformation transformation = new JarTransformation(List.of(unchanged), folder.resolve("transformed"));

		assertSame(jar, transformation.transform(descriptor("greeting"), jar));
		assertSame(jar, transformation.transform(descriptor("greeting"), jar));
		assertEquals(1, unchanged.transformed.size());
		assertFalse(Files.exists(folder.resolve("transformed/greeting-unchanged-1.jar")));
	}

	@Test
	void anExtensionATransformerDoesNotApplyToIsNotRead() throws Exception {
		Path jar = jar("greeting");
		Marking other = new Marking("other") {
			@Override
			public boolean appliesTo(@NotNull ExtensionDescriptor extension) {
				return !extension.getId().equals("greeting");
			}
		};

		assertSame(jar, new JarTransformation(List.of(other), folder.resolve("transformed")).transform(descriptor("greeting"), jar));
		assertTrue(other.transformed.isEmpty());
	}

	@Test
	void aTransformerThatReturnsAnotherFileFailsAndLeavesNoTemporaryFile() throws Exception {
		Path jar = jar("greeting");
		Path elsewhere = folder.resolve("elsewhere.jar");
		ExtensionTransformer stray = new Marking("stray") {
			@Override
			public @NotNull Path transform(@NotNull ExtensionDescriptor extension, @NotNull Path input, @NotNull Path output) {
				return elsewhere;
			}
		};

		IOException failure = assertThrows(IOException.class,
				() -> new JarTransformation(List.of(stray), folder.resolve("transformed")).transform(descriptor("greeting"), jar));

		assertEquals("The extension transformer 'stray' failed on greeting.jar: it returned " + elsewhere + " instead of its input or output",
				failure.getMessage());
		try (var left = Files.list(folder.resolve("transformed"))) {
			assertEquals(0, left.count());
		}
	}

	@Test
	void twoTransformersWithOneIdAreRefused() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new JarTransformation(List.of(new Marking("same"), new Marking("same", "2")), folder));

		assertEquals("Two extension transformers have the id 'same'", failure.getMessage());
	}

	private Path jar(String id) throws IOException {
		return ExtensionJar.of("test-module.yml", "").write(folder.resolve(id + ".jar"));
	}

	private static ExtensionDescriptor descriptor(String id) throws Exception {
		String text = "id: " + id + "\nversion: 1.0.0\nmain: org.example.Main\n";

		return new DescriptorReader().read(id, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}
}
