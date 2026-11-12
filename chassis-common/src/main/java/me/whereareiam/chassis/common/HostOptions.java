package me.whereareiam.chassis.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import me.whereareiam.chassis.ExtensionPreparation;
import me.whereareiam.chassis.ExtensionTransformer;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * How a host loads its kind of extension. Arbor's, for example:
 *
 * <pre>{@code
 * HostOptions options = HostOptions.builder()
 *         .kind("module")
 *         .folder(arborFolder.resolve("modules"))
 *         .descriptorFile("arbor-module.yml")
 *         .dataFolder(arborFolder.resolve("module-data"))
 *         .selectionFile(arborFolder.resolve("modules.yml"))
 *         .hostVersion(Version.parse("1.0.0"))
 *         .offer("gateway", Version.parse("1.0.0"))
 *         .platform(new NeoForgePlatform())
 *         .exportLayer(new ExportLayer(Arbor.class.getClassLoader(), List.of("net.oraylen.arbor.api", "net.kyori.adventure")))
 *         .build();
 * }</pre>
 */
@Getter
@Builder
public final class HostOptions {
	/**
	 * The host's word for its extensions, such as {@code module} or {@code provider}; it names the host as an owner of
	 * services and appears in messages.
	 */
	private final @NotNull String kind;
	/**
	 * The folder the extension jars lie in.
	 */
	private final @NotNull Path folder;
	/**
	 * The descriptor's path inside a jar, such as {@code arbor-module.yml}.
	 */
	private final @NotNull String descriptorFile;
	/**
	 * The folder that holds each extension's own folder, {@code <dataFolder>/<id>}.
	 */
	private final @NotNull Path dataFolder;
	/**
	 * The file that keeps which extensions an administrator turned off.
	 */
	private final @NotNull Path selectionFile;
	/**
	 * The version of the host's API, which descriptors' {@code host} must accept.
	 */
	private final @NotNull Version hostVersion;
	/**
	 * What the host offers for descriptors' {@code requires}, by name, with its version.
	 */
	@Singular("offer")
	private final @NotNull Map<String, Version> offered;
	private final @NotNull Platform platform;
	private final @NotNull ExportLayer exportLayer;
	/**
	 * Change each extension's jar before its class loader is made, in this order, each on the result of the previous
	 * one, such as to relocate a package the host ships under another name. None by default.
	 */
	@Singular
	private final @NotNull List<ExtensionTransformer> transformers;
	/**
	 * The folder the transformers' results are kept in, so a jar is transformed again only when it or a transformer's
	 * version changes. Defaults to {@code <dataFolder>/.transformed}, which no extension's id can name.
	 */
	private final @Nullable Path transformedFolder;
	/**
	 * Prepares each extension's class loader before its main class is loaded, such as
	 * {@code chassis-adapter-attache}'s libraries. Defaults to {@link ExtensionPreparation#NONE}.
	 */
	@Builder.Default
	private final @NotNull ExtensionPreparation preparation = ExtensionPreparation.NONE;
	/**
	 * Gives the logger of a name: the host's kind word, or an extension's id. Defaults to {@link System#getLogger}.
	 */
	@Builder.Default
	private final @NotNull Function<String, System.Logger> loggers = System::getLogger;
}
