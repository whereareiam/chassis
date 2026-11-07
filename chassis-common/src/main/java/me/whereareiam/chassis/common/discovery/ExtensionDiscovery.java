package me.whereareiam.chassis.common.discovery;

import me.whereareiam.chassis.exception.DescriptorException;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds the extension jars in a host's folder and reads the descriptor of each.
 */
public final class ExtensionDiscovery {
	private final @NotNull Path folder;
	private final @NotNull String descriptorFile;
	private final @NotNull DescriptorReader reader = new DescriptorReader();

	/**
	 * Makes the discovery.
	 *
	 * @param folder         the folder of extension jars; made when it is missing
	 * @param descriptorFile the descriptor's path inside a jar, such as {@code arbor-module.yml}
	 */
	public ExtensionDiscovery(@NotNull Path folder, @NotNull String descriptorFile) {
		this.folder = folder;
		this.descriptorFile = descriptorFile;
	}

	/**
	 * Reads every {@code .jar} directly in the folder, in the order of their file names. A second jar with an id
	 * already found is reported as failed rather than replacing the first.
	 *
	 * @return one entry per jar
	 * @throws IOException when the folder cannot be made or listed
	 */
	public @NotNull List<Found> discover() throws IOException {
		Files.createDirectories(folder);

		List<Path> jars;
		try (Stream<Path> files = Files.list(folder)) {
			jars = files.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).sorted().toList();
		}

		List<Found> found = new ArrayList<>();
		Map<String, Path> seen = new HashMap<>();
		for (Path jar : jars) {
			Found entry = read(jar);
			if (entry.descriptor() != null) {
				Path earlier = seen.putIfAbsent(entry.descriptor().getId(), jar);
				if (earlier != null) entry = new Found(jar, null, new ExtensionException(entry.descriptor().getId(),
						"Both " + earlier.getFileName() + " and " + jar.getFileName() + " are the extension '" + entry.descriptor().getId() + "'; " + jar.getFileName() + " is left out"));
			}
			found.add(entry);
		}

		return found;
	}

	/**
	 * Reads the descriptor of one jar.
	 *
	 * @param jar the jar
	 * @return the jar with its descriptor, or with why it could not be read
	 */
	public @NotNull Found read(@NotNull Path jar) {
		String source = jar.getFileName().toString();
		try (JarFile file = new JarFile(jar.toFile())) {
			var entry = file.getJarEntry(descriptorFile);
			if (entry == null) return new Found(jar, null, new DescriptorException(source, source + " has no " + descriptorFile));
			try (InputStream input = file.getInputStream(entry)) {
				return new Found(jar, reader.read(source, input), null);
			}
		} catch (ExtensionException e) {
			return new Found(jar, null, e);
		} catch (IOException e) {
			return new Found(jar, null, new ExtensionException(source, source + " cannot be read as a jar: " + e.getMessage(), e));
		}
	}

	/**
	 * A jar the discovery found.
	 *
	 * @param jar        the jar
	 * @param descriptor its descriptor, or {@code null} when it could not be read
	 * @param failure    why it could not be read, or {@code null}
	 */
	public record Found(@NotNull Path jar, @Nullable ExtensionDescriptor descriptor, @Nullable ExtensionException failure) {
		/**
		 * Returns the extension's id, or the jar's file name without {@code .jar} when no descriptor was read.
		 *
		 * @return the id
		 */
		public @NotNull String id() {
			if (descriptor != null) return descriptor.getId();
			String name = jar.getFileName().toString();

			return name.substring(0, name.length() - ".jar".length());
		}
	}
}
