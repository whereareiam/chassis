package me.whereareiam.chassis.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * An extension a host found, as it stands at the moment it was read.
 */
@Getter
@Builder
@ToString
public final class InstalledExtension {
	/**
	 * The id from the descriptor, or the jar's file name when its descriptor could not be read.
	 */
	private final @NotNull String id;
	/**
	 * The descriptor, or {@code null} when it could not be read.
	 */
	private final @Nullable ExtensionDescriptor descriptor;
	private final @NotNull Path jar;
	private final @NotNull ExtensionState state;
	/**
	 * Why the extension waits: one line per missing dependency, plugin or requirement. Empty unless it waits.
	 */
	@Builder.Default
	private final @NotNull List<String> waitingFor = List.of();
	/**
	 * Why it failed, or {@code null} unless it failed.
	 */
	private final @Nullable Throwable failure;
}
