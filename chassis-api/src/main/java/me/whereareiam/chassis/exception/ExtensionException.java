package me.whereareiam.chassis.exception;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension could not be found, read, loaded or started.
 */
@Getter
public class ExtensionException extends Exception {
	/**
	 * The extension's id, or the jar's file name when no id could be read.
	 */
	private final @NotNull String extension;

	public ExtensionException(@NotNull String extension, @NotNull String message) {
		super(message);
		this.extension = extension;
	}

	public ExtensionException(@NotNull String extension, @NotNull String message, @Nullable Throwable cause) {
		super(message, cause);
		this.extension = extension;
	}
}
