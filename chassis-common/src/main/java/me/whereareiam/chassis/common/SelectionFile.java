package me.whereareiam.chassis.common;

import me.whereareiam.configura.Config;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The file that keeps which extensions an administrator turned off, so they stay off across restarts. Their jars stay
 * in the folder. An administrator may also edit it while the host is stopped:
 *
 * <pre>{@code
 * disabled:
 *   - shop
 * }</pre>
 */
public final class SelectionFile {
	private final @NotNull Path file;
	private final @NotNull String kind;

	/**
	 * Names the file.
	 *
	 * @param file the file; missing means nothing is turned off
	 * @param kind the host's word for its extensions, for the file's comment
	 */
	public SelectionFile(@NotNull Path file, @NotNull String kind) {
		this.file = file;
		this.kind = kind;
	}

	/**
	 * Reads the ids that are turned off.
	 *
	 * @return the ids
	 * @throws IOException when the file exists and cannot be read
	 */
	public @NotNull Set<String> disabled() throws IOException {
		if (!Files.exists(file)) return new TreeSet<>();

		try (InputStream input = Files.newInputStream(file)) {
			Selection selection = Config.configured().read(input, Selection.class);

			return selection == null || selection.disabled == null ? new TreeSet<>() : new TreeSet<>(selection.disabled);
		} catch (RuntimeException e) {
			throw new IOException(file + " is not valid YAML: " + e.getMessage(), e);
		}
	}

	/**
	 * Writes the ids that are turned off, replacing the file at once.
	 *
	 * @param disabled the ids
	 * @throws IOException when the file cannot be written
	 */
	public void write(@NotNull Set<String> disabled) throws IOException {
		StringBuilder text = new StringBuilder()
				.append("# The ").append(kind).append("s an administrator turned off; their jars stay in the folder.\n")
				.append("disabled:");
		if (disabled.isEmpty()) text.append(" []");
		for (String id : new TreeSet<>(disabled)) text.append("\n  - ").append(id);
		text.append('\n');

		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
		Files.writeString(temporary, text, StandardCharsets.UTF_8);
		Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	@SuppressWarnings("unused")
	private static final class Selection {
		private List<String> disabled = new ArrayList<>();
	}
}
