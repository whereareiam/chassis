package me.whereareiam.chassis.common.discovery;

import me.whereareiam.chassis.exception.DescriptorException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.model.VersionRange;
import me.whereareiam.configura.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads an extension's descriptor file, YAML in the shape {@link ExtensionDescriptor} shows, and checks it.
 */
public final class DescriptorReader {
	private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
	private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

	/**
	 * Reads and checks a descriptor.
	 *
	 * @param source names the jar in messages
	 * @param input  the descriptor file
	 * @return the descriptor
	 * @throws DescriptorException when a required field is missing or a field is malformed
	 */
	public @NotNull ExtensionDescriptor read(@NotNull String source, @NotNull InputStream input) throws DescriptorException {
		return read(source, input, null).descriptor();
	}

	/**
	 * Reads and checks a descriptor, and reads the host's own fields of the same file into a type of the host.
	 *
	 * @param source names the jar in messages
	 * @param input  the descriptor file
	 * @param fields the type, whose fields name the host's fields of the descriptor; {@code null} for none
	 * @param <F>    that type
	 * @return the descriptor with the host's fields
	 * @throws DescriptorException when a required field is missing, a field is malformed, or the host's fields cannot be
	 *                             read into the type
	 */
	public <F> @NotNull Read<F> read(@NotNull String source, @NotNull InputStream input, @Nullable Class<F> fields) throws DescriptorException {
		byte[] text;
		try {
			text = input.readAllBytes();
		} catch (IOException e) {
			throw new DescriptorException(source, "The descriptor of " + source + " cannot be read: " + e.getMessage(), e);
		}

		ExtensionDescriptor descriptor = descriptor(source, text);
		if (fields == null) return new Read<>(descriptor, null);
		try {
			return new Read<>(descriptor, Config.configured().read(new ByteArrayInputStream(text), fields));
		} catch (RuntimeException e) {
			throw new DescriptorException(descriptor.getId(), "The descriptor of " + descriptor.getId()
					+ " has fields of the host that cannot be read: " + e.getMessage(), e);
		}
	}

	private static ExtensionDescriptor descriptor(String source, byte[] text) throws DescriptorException {
		DescriptorFile file;
		try {
			file = Config.configured().read(new ByteArrayInputStream(text), DescriptorFile.class);
		} catch (RuntimeException e) {
			throw new DescriptorException(source, "The descriptor of " + source + " is not valid YAML: " + e.getMessage(), e);
		}
		if (file == null) throw new DescriptorException(source, "The descriptor of " + source + " is empty");

		String id = required(source, "id", file.id);
		if (!ID.matcher(id).matches())
			throw new DescriptorException(source, "The id '" + id + "' of " + source + " is not lowercase letters, digits and hyphens starting with a letter");

		ExtensionDescriptor.ExtensionDescriptorBuilder descriptor = ExtensionDescriptor.builder()
				.id(id)
				.name(file.name == null || file.name.isBlank() ? id : file.name)
				.version(version(id, required(id, "version", file.version)))
				.main(required(id, "main", file.main))
				.description(file.description)
				.authors(file.authors == null ? List.of() : file.authors)
				.host(range(id, "host", file.host))
				.exports(packages(id, file.exports))
				.platforms(platforms(id, file.platforms))
				.peers(peers(id, file.peers));
		if (file.requires != null)
			file.requires.forEach((name, range) -> descriptor.require(name, rangeUnchecked(id, "requires." + name, range)));
		for (DependencyEntry dependency : file.dependencies == null ? List.<DependencyEntry>of() : file.dependencies)
			descriptor.dependency(ExtensionDescriptor.Dependency.builder()
					.id(required(id, "dependencies[].id", dependency.id))
					.version(range(id, "dependencies." + dependency.id + ".version", dependency.version))
					.optional(dependency.optional)
					.build());

		try {
			return descriptor.build();
		} catch (IllegalArgumentException e) {
			throw new DescriptorException(id, e.getMessage(), e);
		}
	}

	/**
	 * A descriptor as read, with the host's own fields of it.
	 *
	 * @param descriptor the descriptor
	 * @param fields     the host's fields, or {@code null} when the host reads none
	 * @param <F>        the type the host reads its fields into
	 */
	public record Read<F>(@NotNull ExtensionDescriptor descriptor, @Nullable F fields) {
	}

	private static String required(String source, String field, @Nullable String value) throws DescriptorException {
		if (value == null || value.isBlank())
			throw new DescriptorException(source, "The descriptor of " + source + " names no '" + field + "'");

		return value.trim();
	}

	private static Version version(String id, String text) throws DescriptorException {
		try {
			return Version.parse(text);
		} catch (IllegalArgumentException e) {
			throw new DescriptorException(id, "The version '" + text + "' of " + id + " is not a version", e);
		}
	}

	private static VersionRange range(String id, String field, @Nullable String text) throws DescriptorException {
		try {
			return text == null ? VersionRange.ANY : VersionRange.parse(text);
		} catch (IllegalArgumentException e) {
			throw new DescriptorException(id, "The " + field + " '" + text + "' of " + id + " is not a version range", e);
		}
	}

	private static VersionRange rangeUnchecked(String id, String field, @Nullable String text) {
		try {
			return range(id, field, text);
		} catch (DescriptorException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	private static List<String> packages(String id, @Nullable List<String> values) throws DescriptorException {
		if (values == null) return List.of();

		List<String> packages = new ArrayList<>();
		for (String value : values) {
			String name = value == null ? "" : value.trim();
			if (!PACKAGE.matcher(name).matches())
				throw new DescriptorException(id, "The export '" + value + "' of " + id + " is not a package name");
			packages.add(name);
		}

		return packages;
	}

	private static List<ExtensionDescriptor.PeerDependency> peers(String id, @Nullable List<PeerEntry> entries) throws DescriptorException {
		if (entries == null) return List.of();

		List<ExtensionDescriptor.PeerDependency> peers = new ArrayList<>();
		for (PeerEntry entry : entries) {
			String peer = required(id, "peers[].id", entry.id);
			peers.add(ExtensionDescriptor.PeerDependency.builder()
					.id(peer)
					.version(range(id, "peers." + peer + ".version", entry.version))
					.optional(entry.optional)
					.packages(packages(id, entry.packages))
					.build());
		}

		return peers;
	}

	private static List<ExtensionDescriptor.SupportedPlatform> platforms(String id, @Nullable List<PlatformEntry> entries) throws DescriptorException {
		if (entries == null) return List.of();

		List<ExtensionDescriptor.SupportedPlatform> platforms = new ArrayList<>();
		for (PlatformEntry entry : entries) {
			String name = required(id, "platforms[].name", entry.name).toLowerCase(Locale.ROOT);
			platforms.add(ExtensionDescriptor.SupportedPlatform.builder()
					.name(name)
					.version(range(id, "platforms." + name + ".version", entry.version))
					.build());
		}

		return platforms;
	}

	// The file as YAML gives it; read field by field, so no setters.
	@SuppressWarnings("unused")
	private static final class DescriptorFile {
		private String id;
		private String name;
		private String version;
		private String main;
		private String description;
		private List<String> authors;
		private String host;
		private Map<String, String> requires = new LinkedHashMap<>();
		private List<DependencyEntry> dependencies;
		private List<String> exports;
		private List<PlatformEntry> platforms;
		private List<PeerEntry> peers;
	}

	@SuppressWarnings("unused")
	private static final class PlatformEntry {
		private String name;
		private String version;
	}

	@SuppressWarnings("unused")
	private static final class PeerEntry {
		private String id;
		private String version;
		private boolean optional;
		private List<String> packages;
	}

	@SuppressWarnings("unused")
	private static final class DependencyEntry {
		private String id;
		private String version;
		private boolean optional;
	}
}
