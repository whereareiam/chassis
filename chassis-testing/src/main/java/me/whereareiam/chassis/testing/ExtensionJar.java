package me.whereareiam.chassis.testing;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Writes an extension jar for a test: a descriptor and classes taken from the test's own class path, with their
 * nested classes. Like a jar a build writes, it holds an entry for each folder, so class path scanning finds its
 * packages. The classes must not lie in a package the host exports, or the host's copy is used instead.
 *
 * <pre>{@code
 * Path jar = ExtensionJar.of("arbor-module.yml", """
 *         id: greeting
 *         version: 1.0.0
 *         main: example.GreetingModule
 *         """)
 *         .with(GreetingModule.class)
 *         .write(modules.resolve("greeting.jar"));
 * }</pre>
 */
public final class ExtensionJar {
	private final Map<String, byte[]> files = new LinkedHashMap<>();
	private final List<Class<?>> classes = new ArrayList<>();

	private ExtensionJar() {
	}

	/**
	 * Starts a jar with its descriptor.
	 *
	 * @param descriptorFile the descriptor's path inside the jar, as the host names it
	 * @param descriptor     the descriptor's text
	 * @return the jar to fill
	 */
	public static @NotNull ExtensionJar of(@NotNull String descriptorFile, @NotNull String descriptor) {
		return new ExtensionJar().file(descriptorFile, descriptor);
	}

	/**
	 * Adds classes with their nested and anonymous classes.
	 *
	 * @param types the classes, compiled on the test's class path
	 * @return this jar
	 */
	public @NotNull ExtensionJar with(@NotNull Class<?>... types) {
		classes.addAll(List.of(types));

		return this;
	}

	/**
	 * Adds a text file.
	 *
	 * @param path    its path inside the jar
	 * @param content its text
	 * @return this jar
	 */
	public @NotNull ExtensionJar file(@NotNull String path, @NotNull String content) {
		files.put(path, content.getBytes(StandardCharsets.UTF_8));

		return this;
	}

	/**
	 * Writes the jar, replacing a file already there.
	 *
	 * @param target where to write it
	 * @return the target
	 * @throws IOException when a class cannot be read or the jar cannot be written
	 */
	public @NotNull Path write(@NotNull Path target) throws IOException {
		Files.createDirectories(target.toAbsolutePath().getParent());
		Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName().toString(), ".tmp");
		try (OutputStream output = Files.newOutputStream(temporary); JarOutputStream jar = new JarOutputStream(output)) {
			Set<String> folders = new HashSet<>();
			for (Map.Entry<String, byte[]> file : files.entrySet()) put(jar, folders, file.getKey(), file.getValue());
			for (Class<?> type : classes) addClass(jar, folders, type);
		}
		Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

		return target;
	}

	private void addClass(JarOutputStream jar, Set<String> folders, Class<?> type) throws IOException {
		String path = type.getName().replace('.', '/') + ".class";
		put(jar, folders, path, bytes(type, path));

		// Nested, local and anonymous classes: Outer$1, Outer$Inner, found next to the outer class's file.
		String prefix = type.getName().substring(type.getName().lastIndexOf('.') + 1) + "$";
		Path folder = folderOf(type);
		if (folder == null) return;
		try (var siblings = Files.list(folder)) {
			for (Path sibling : siblings.filter(file -> file.getFileName().toString().startsWith(prefix)).sorted().toList()) {
				String nested = path.substring(0, path.lastIndexOf('/') + 1) + sibling.getFileName();
				put(jar, folders, nested, Files.readAllBytes(sibling));
			}
		}
	}

	private static Path folderOf(Class<?> type) {
		try {
			var url = type.getResource(type.getSimpleName() + ".class");
			if (url == null || !url.getProtocol().equals("file")) return null;

			return Path.of(url.toURI()).getParent();
		} catch (java.net.URISyntaxException e) {
			return null;
		}
	}

	private static byte[] bytes(Class<?> type, String path) throws IOException {
		try (InputStream input = type.getClassLoader().getResourceAsStream(path)) {
			if (input == null) throw new IOException("No class file for " + type.getName());

			return input.readAllBytes();
		}
	}

	private static void put(JarOutputStream jar, Set<String> folders, String path, byte[] content) throws IOException {
		for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
			String folder = path.substring(0, slash + 1);
			if (!folders.add(folder)) continue;

			jar.putNextEntry(new JarEntry(folder));
			jar.closeEntry();
		}

		jar.putNextEntry(new JarEntry(path));
		jar.write(content);
		jar.closeEntry();
	}
}
