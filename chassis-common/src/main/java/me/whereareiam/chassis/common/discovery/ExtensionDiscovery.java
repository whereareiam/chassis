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
 * Finds the extension jars in a host's folder and reads the descriptor of each, with the host's own fields of it.
 *
 * @param <F> the type the host reads its fields of a descriptor into
 */
public final class ExtensionDiscovery<F> {
	private final @NotNull Path folder;
	private final @NotNull String descriptorFile;
	private final @Nullable Class<F> fields;
	private final @NotNull DescriptorReader reader = new DescriptorReader();

	/**
	 * Makes the discovery.
	 *
	 * @param folder         the folder of extension jars; made when it is missing
	 * @param descriptorFile the descriptor's path inside a jar, such as {@code arbor-module.yml}
	 */
	public ExtensionDiscovery(@NotNull Path folder, @NotNull String descriptorFile) {
		this(folder, descriptorFile, null);
	}

	/**
	 * Makes the discovery of a host that reads fields of its own from each descriptor.
	 *
	 * @param folder         the folder of extension jars; made when it is missing
	 * @param descriptorFile the descriptor's path inside a jar, such as {@code arbor-module.yml}
	 * @param fields         the type the host reads its fields into; {@code null} for none
	 */
	public ExtensionDiscovery(@NotNull Path folder, @NotNull String descriptorFile, @Nullable Class<F> fields) {
		this.folder = folder;
		this.descriptorFile = descriptorFile;
		this.fields = fields;
	}

	/**
	 * Reads every {@code .jar} directly in the folder, in the order of their file names. A second jar with an id
	 * already found is reported as failed rather than replacing the first.
	 *
	 * @return one entry per jar
	 * @throws IOException when the folder cannot be made or listed
	 */
	public @NotNull List<Found<F>> discover() throws IOException {
		Files.createDirectories(folder);

		List<Path> jars;
		try (Stream<Path> files = Files.list(folder)) {
			jars = files.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).sorted().toList();
		}

		List<Found<F>> found = new ArrayList<>();
		Map<String, Path> seen = new HashMap<>();
		for (Path jar : jars) {
			Found<F> entry = read(jar);
			if (entry.descriptor() != null) {
				Path earlier = seen.putIfAbsent(entry.descriptor().getId(), jar);
				if (earlier != null) entry = Found.failed(jar, new ExtensionException(entry.descriptor().getId(),
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
	public @NotNull Found<F> read(@NotNull Path jar) {
		String source = jar.getFileName().toString();
		try (JarFile file = new JarFile(jar.toFile())) {
			var entry = file.getJarEntry(descriptorFile);
			if (entry == null) return Found.failed(jar, new DescriptorException(source, source + " has no " + descriptorFile));
			try (InputStream input = file.getInputStream(entry)) {
				DescriptorReader.Read<F> read = reader.read(source, input, fields);
				return new Found<>(jar, read.descriptor(), read.fields(), null);
			}
		} catch (ExtensionException e) {
			return Found.failed(jar, e);
		} catch (IOException e) {
			return Found.failed(jar, new ExtensionException(source, source + " cannot be read as a jar: " + e.getMessage(), e));
		}
	}

	/**
	 * A jar the discovery found.
	 *
	 * @param jar        the jar
	 * @param descriptor its descriptor, or {@code null} when it could not be read
	 * @param fields     the host's own fields of its descriptor, or {@code null} when it could not be read or the host
	 *                   reads none
	 * @param failure    why it could not be read, or {@code null}
	 * @param <F>        the type the host reads its fields into
	 */
	public record Found<F>(
			@NotNull Path jar,
			@Nullable ExtensionDescriptor descriptor,
			@Nullable F fields,
			@Nullable ExtensionException failure
	) {
		/**
		 * Makes a jar whose descriptor could not be read.
		 *
		 * @param jar     the jar
		 * @param failure why
		 * @param <F>     the type the host reads its fields into
		 * @return the jar
		 */
		public static <F> @NotNull Found<F> failed(@NotNull Path jar, @NotNull ExtensionException failure) {
			return new Found<>(jar, null, null, failure);
		}

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
