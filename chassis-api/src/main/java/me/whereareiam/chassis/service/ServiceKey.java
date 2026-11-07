package me.whereareiam.chassis.service;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Names a service: its type, and a name that tells apart several services of one type.
 *
 * @param <T> the service's type
 */
@Getter
@EqualsAndHashCode
public final class ServiceKey<T> {
	private final @NotNull Class<T> type;
	private final @NotNull String name;

	private ServiceKey(@NotNull Class<T> type, @NotNull String name) {
		this.type = type;
		this.name = name;
	}

	/**
	 * Names the service of a type.
	 *
	 * @param type the service's type
	 * @param <T>  the service's type
	 * @return the key named {@code default}
	 */
	public static <T> @NotNull ServiceKey<T> of(@NotNull Class<T> type) {
		return new ServiceKey<>(type, "default");
	}

	/**
	 * Names one of several services of a type.
	 *
	 * @param type the service's type
	 * @param name the name, such as {@code console}
	 * @param <T>  the service's type
	 * @return the key
	 */
	public static <T> @NotNull ServiceKey<T> of(@NotNull Class<T> type, @NotNull String name) {
		if (name.isBlank()) throw new IllegalArgumentException("A service key needs a name");

		return new ServiceKey<>(type, name);
	}

	@Override
	public String toString() {
		return type.getName() + (name.equals("default") ? "" : "#" + name);
	}
}
