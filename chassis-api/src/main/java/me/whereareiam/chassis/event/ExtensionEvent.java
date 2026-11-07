package me.whereareiam.chassis.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An installed extension changed its state.
 */
@Getter
@Builder
@ToString
public final class ExtensionEvent {
	/**
	 * The extension after the change.
	 */
	private final @NotNull InstalledExtension extension;
	/**
	 * The state before, or {@code null} when the extension was just installed.
	 */
	private final @Nullable ExtensionState previous;
}
