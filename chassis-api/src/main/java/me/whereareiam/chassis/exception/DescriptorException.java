package me.whereareiam.chassis.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension jar has no descriptor, or its descriptor is incomplete or malformed.
 */
public class DescriptorException extends ExtensionException {
	public DescriptorException(@NotNull String extension, @NotNull String message) {
		super(extension, message);
	}

	public DescriptorException(@NotNull String extension, @NotNull String message, @Nullable Throwable cause) {
		super(extension, message, cause);
	}
}
