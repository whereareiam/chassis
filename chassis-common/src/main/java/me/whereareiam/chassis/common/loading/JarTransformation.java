package me.whereareiam.chassis.common.loading;

import me.whereareiam.chassis.ExtensionTransformer;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runs a host's {@link ExtensionTransformer}s on an extension's jar and keeps their results in one folder. A result
 * is named after the jar it was made from, with each transformer's id and version appended; a marker beside it holds
 * the SHA-256 of that source, so a result is reused only while its source is unchanged. The marker also records when
 * a transformer left a jar unchanged.
 */
public final class JarTransformation {
	private static final Pattern UNSAFE_NAME = Pattern.compile("[^A-Za-z0-9._-]");
	private static final String UNCHANGED = " unchanged";

	private final @NotNull List<ExtensionTransformer> transformers;
	private final @NotNull Path folder;

	/**
	 * Makes the transformation.
	 *
	 * @param transformers the transformers, in the order they run; their ids must differ
	 * @param folder       the folder transformed jars are kept in; made when a jar is first transformed
	 */
	public JarTransformation(@NotNull List<ExtensionTransformer> transformers, @NotNull Path folder) {
		Set<String> ids = new HashSet<>();
		for (ExtensionTransformer transformer : transformers) {
			if (transformer.getId().isBlank()) throw new IllegalArgumentException("An extension transformer needs an id");
			if (!ids.add(transformer.getId()))
				throw new IllegalArgumentException("Two extension transformers have the id '" + transformer.getId() + "'");
		}
		this.transformers = List.copyOf(transformers);
		this.folder = folder.toAbsolutePath().normalize();
	}

	/**
	 * Runs every transformer that applies to the extension on its jar, in order.
	 *
	 * @param descriptor the extension's descriptor
	 * @param jar        its installed jar
	 * @return the jar to load: {@code jar} itself when no transformer changed it
	 * @throws IOException when a jar cannot be read or written, or a transformer fails
	 */
	public @NotNull Path transform(@NotNull ExtensionDescriptor descriptor, @NotNull Path jar) throws IOException {
		Path current = jar;
		String name = withoutJar(jar.getFileName().toString());
		for (ExtensionTransformer transformer : transformers) {
			if (!transformer.appliesTo(descriptor)) continue;

			name = name + '-' + safe(transformer.getId()) + '-' + safe(transformer.getVersion());
			current = transformOnce(transformer, descriptor, current, folder.resolve(name + ".jar"));
		}

		return current;
	}

	private static Path transformOnce(ExtensionTransformer transformer, ExtensionDescriptor descriptor, Path input, Path result)
			throws IOException {
		String fingerprint = sha256(input);
		Path marker = result.resolveSibling(result.getFileName() + ".source.sha256");
		if (Files.isRegularFile(marker)) {
			String recorded = Files.readString(marker);
			if (recorded.equals(fingerprint + UNCHANGED)) return input;
			if (recorded.equals(fingerprint) && Files.isRegularFile(result)) return result;
		}

		Files.createDirectories(result.getParent());
		Files.deleteIfExists(marker);
		Path output = Files.createTempFile(result.getParent(), result.getFileName().toString(), ".tmp");
		try {
			Path written = transformer.transform(descriptor, input, output);
			if (written.equals(input)) {
				Files.deleteIfExists(result);
				Files.writeString(marker, fingerprint + UNCHANGED);

				return input;
			}
			if (!written.equals(output))
				throw new IllegalStateException("it returned " + written + " instead of its input or output");

			Files.move(output, result, StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(marker, fingerprint);

			return result;
		} catch (IOException | RuntimeException failure) {
			throw new IOException("The extension transformer '" + transformer.getId() + "' failed on " + input.getFileName()
					+ ": " + failure.getMessage(), failure);
		} finally {
			Files.deleteIfExists(output);
		}
	}

	private static String sha256(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java platform has SHA-256", e);
		}
		try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
			input.transferTo(OutputStream.nullOutputStream());
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	private static String withoutJar(String name) {
		return name.endsWith(".jar") ? name.substring(0, name.length() - ".jar".length()) : name;
	}

	private static String safe(String value) {
		return UNSAFE_NAME.matcher(value).replaceAll("_");
	}
}
