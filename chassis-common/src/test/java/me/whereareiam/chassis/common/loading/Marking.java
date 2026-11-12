package me.whereareiam.chassis.common.loading;

import me.whereareiam.chassis.ExtensionTransformer;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A transformer for tests: it copies a jar and adds {@code transformed.txt}, which names each transformer that marked
 * the jar, and records the jars it was given.
 */
public class Marking implements ExtensionTransformer {
	private final @NotNull String id;
	private final @NotNull String version;
	/**
	 * The file names of the jars it transformed, in order.
	 */
	public final List<String> transformed = new ArrayList<>();

	/**
	 * Makes the transformer at version {@code 1}.
	 *
	 * @param id its id
	 */
	public Marking(@NotNull String id) {
		this(id, "1");
	}

	/**
	 * Makes the transformer.
	 *
	 * @param id      its id
	 * @param version its version
	 */
	public Marking(@NotNull String id, @NotNull String version) {
		this.id = id;
		this.version = version;
	}

	@Override
	public @NotNull String getId() {
		return id;
	}

	@Override
	public @NotNull String getVersion() {
		return version;
	}

	@Override
	public @NotNull Path transform(@NotNull ExtensionDescriptor extension, @NotNull Path input, @NotNull Path output) throws IOException {
		transformed.add(input.getFileName().toString());
		String marks = id;
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(input));
			 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				if (entry.getName().equals("transformed.txt")) {
					marks = new String(in.readAllBytes(), StandardCharsets.UTF_8) + "," + id;
					continue;
				}
				out.putNextEntry(new ZipEntry(entry.getName()));
				in.transferTo(out);
				out.closeEntry();
			}
			out.putNextEntry(new ZipEntry("transformed.txt"));
			out.write(marks.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}

		return output;
	}

	/**
	 * Reads which transformers marked a jar.
	 *
	 * @param jar the jar
	 * @return the ids, joined by commas, or {@code null} when none marked it
	 * @throws IOException when the jar cannot be read
	 */
	public static String marks(Path jar) throws IOException {
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry())
				if (entry.getName().equals("transformed.txt")) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		return null;
	}
}
