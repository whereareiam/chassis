package me.whereareiam.chassis.common.discovery;

import me.whereareiam.chassis.exception.DescriptorException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.model.VersionRange;
import me.whereareiam.configura.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
		DescriptorFile file;
		try {
			file = Config.configured().read(input, DescriptorFile.class);
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
				.platforms(lower(file.platforms))
				.plugins(file.plugins == null ? List.of() : file.plugins);
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

	private static List<String> lower(@Nullable List<String> values) {
		if (values == null) return List.of();
		List<String> lowered = new ArrayList<>();
		for (String value : values) lowered.add(value.trim().toLowerCase(java.util.Locale.ROOT));

		return lowered;
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
		private List<String> platforms;
		private List<String> plugins;
	}

	@SuppressWarnings("unused")
	private static final class DependencyEntry {
		private String id;
		private String version;
		private boolean optional;
	}
}
