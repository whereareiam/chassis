package me.whereareiam.chassis.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.chassis.service.ServiceKey;
import org.jetbrains.annotations.NotNull;

/**
 * A service came or went.
 *
 * @param <T> the service's type
 */
@Getter
@Builder
@ToString
public final class ServiceEvent<T> {
	private final @NotNull ServiceKey<T> key;
	private final @NotNull T service;
	/**
	 * Who registered it: an extension's id, or the host's kind word for the host itself.
	 */
	private final @NotNull String owner;
	private final int priority;
	/**
	 * Whether the service came; {@code false} when it went.
	 */
	private final boolean registered;
}
