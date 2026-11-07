package me.whereareiam.chassis.service;

import me.whereareiam.chassis.event.ServiceEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Services the host and its extensions offer each other, by key. Several may be registered under one key: the one with
 * the highest priority answers {@link #find(ServiceKey)}, the one registered first among equals.
 *
 * <p>Seen through an extension's context, what the extension registers or watches belongs to it, and the host removes
 * it when the extension stops; watchers are told that its services went. A service type must be one both sides see,
 * such as a type of the host's API or of an extension the other depends on.</p>
 */
public interface Services {
	/**
	 * Registers a service with priority {@code 0}.
	 *
	 * @param key     the service's key
	 * @param service the service
	 * @param <T>     the service's type
	 * @return removes the service
	 */
	default <T> @NotNull ServiceRegistration register(@NotNull ServiceKey<T> key, @NotNull T service) {
		return register(key, service, 0);
	}

	/**
	 * Registers a service.
	 *
	 * @param key      the service's key
	 * @param service  the service
	 * @param priority higher answers first
	 * @param <T>      the service's type
	 * @return removes the service
	 */
	<T> @NotNull ServiceRegistration register(@NotNull ServiceKey<T> key, @NotNull T service, int priority);

	/**
	 * Finds the service that answers for a key.
	 *
	 * @param key the key
	 * @param <T> the service's type
	 * @return the service with the highest priority, or empty when none is registered
	 */
	<T> @NotNull Optional<T> find(@NotNull ServiceKey<T> key);

	/**
	 * Lists every service registered for a key.
	 *
	 * @param key the key
	 * @param <T> the service's type
	 * @return the services, highest priority first
	 */
	<T> @NotNull List<T> all(@NotNull ServiceKey<T> key);

	/**
	 * Follows the services of a key: the listener is told of every service registered so far, then of each that
	 * comes or goes.
	 *
	 * @param key      the key
	 * @param listener receives each change, on the thread that made it
	 * @param <T>      the service's type
	 * @return ends the watch
	 */
	<T> @NotNull AutoCloseable watch(@NotNull ServiceKey<T> key, @NotNull Consumer<ServiceEvent<T>> listener);
}
