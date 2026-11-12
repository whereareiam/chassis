package me.whereareiam.chassis;

import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Changes an extension's jar before the extension's class loader is made, such as to relocate a package the host ships
 * under another name. Chassis does not know what a transformer does; it only runs it and keeps its result.
 *
 * <p>The host keeps each transformed jar in its folder of transformed jars, under a name that carries {@link #getId()}
 * and {@link #getVersion()}, and reuses it as long as the jar it was made from is unchanged. A restart or a reload
 * therefore does not transform an extension again, and a new transformer version transforms it anew: change the
 * version whenever the transformer writes different bytes for the same input.</p>
 *
 * <p>A host's transformers run in the order it names them, each on the result of the previous one, every time an
 * extension starts, on the host's thread. The extension's class loader, the host's preparation and
 * {@link ExtensionPreparation.Loader#jar()} read the last result; the extension's installed jar is never changed.</p>
 *
 * <pre>{@code
 * ExtensionTransformer relocation = new ExtensionTransformer() {
 *     public String getId() { return "adventure-relocation"; }
 *     public String getVersion() { return "1"; }
 *     public Path transform(ExtensionDescriptor extension, Path input, Path output) throws IOException {
 *         relocator.relocate(input, output, Map.of("net.kyori.adventure", "org.example.library.adventure"));
 *         return output;
 *     }
 * };
 * }</pre>
 */
public interface ExtensionTransformer {
	/**
	 * Returns the identifier of this transformer, unique among the transformers of one host. It names the transformed
	 * jars, so it should stay the same across releases of the transformer.
	 *
	 * @return the identifier
	 */
	@NotNull String getId();

	/**
	 * Returns the version of this transformer's output. Jars transformed by another version are transformed again.
	 *
	 * @return the version
	 */
	@NotNull String getVersion();

	/**
	 * Tells whether this transformer changes the extension at all. An extension it does not apply to is passed on
	 * without reading its jar.
	 *
	 * @param extension the descriptor of the extension about to load
	 * @return {@code true} when {@link #transform(ExtensionDescriptor, Path, Path)} should run for it
	 */
	default boolean appliesTo(@NotNull ExtensionDescriptor extension) {
		return true;
	}

	/**
	 * Transforms an extension's jar. The transformer reads {@code input} and writes the transformed jar to
	 * {@code output}, an empty temporary file it may replace, which the host moves into place once this returns. When
	 * the jar needs no change, it returns {@code input} and leaves {@code output} alone; the host then loads the input
	 * and remembers that it needs no change.
	 *
	 * @param extension the descriptor of the extension the jar belongs to
	 * @param input     the jar as installed and transformed by the previous transformers
	 * @param output    the file to write the transformed jar to
	 * @return {@code output} when the transformer wrote it, or {@code input} when the jar needs no change
	 * @throws IOException when the jar cannot be read or written; the extension then fails to start
	 */
	@NotNull Path transform(@NotNull ExtensionDescriptor extension, @NotNull Path input, @NotNull Path output) throws IOException;
}
