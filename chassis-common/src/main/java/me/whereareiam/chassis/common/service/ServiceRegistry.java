package me.whereareiam.chassis.common.service;

import me.whereareiam.chassis.event.ServiceEvent;
import me.whereareiam.chassis.service.ServiceKey;
import me.whereareiam.chassis.service.ServiceRegistration;
import me.whereareiam.chassis.service.Services;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The host's services, each registered by an owner. {@link #as(String)} gives an owner its view;
 * {@link #removeOwner(String)} takes away everything an owner registered or watches.
 *
 * <p>Listeners are told after the registry has changed and outside its lock, so a listener may register or find
 * services itself. A listener that throws is reported to the error handler and the others are still told.</p>
 */
public final class ServiceRegistry {
	private final Object lock = new Object();
	private final Map<ServiceKey<?>, List<Entry>> services = new HashMap<>();
	private final Map<ServiceKey<?>, List<Watcher>> watchers = new HashMap<>();
	private final AtomicLong sequence = new AtomicLong();
	private final @NotNull Consumer<Throwable> errors;

	/**
	 * Makes an empty registry.
	 *
	 * @param errors receives what a listener throws
	 */
	public ServiceRegistry(@NotNull Consumer<Throwable> errors) {
		this.errors = errors;
	}

	/**
	 * Returns the services as one owner sees them.
	 *
	 * @param owner the owner's name: an extension's id, or the host's kind word
	 * @return the owner's view
	 */
	public @NotNull Services as(@NotNull String owner) {
		return new OwnedServices(owner);
	}

	/**
	 * Removes every service an owner registered and every watch it holds, and tells the other watchers that the
	 * services went.
	 *
	 * @param owner the owner
	 */
	public void removeOwner(@NotNull String owner) {
		List<Runnable> notices = new ArrayList<>();
		synchronized (lock) {
			// Its own watches go first: an owner that stops is not told what it takes away.
			watchers.values().forEach(list -> list.removeIf(watcher -> watcher.owner.equals(owner)));
			for (Map.Entry<ServiceKey<?>, List<Entry>> byKey : services.entrySet()) {
				for (Entry entry : List.copyOf(byKey.getValue())) {
					if (!entry.owner.equals(owner)) continue;

					byKey.getValue().remove(entry);
					notices.addAll(notices(byKey.getKey(), entry, false));
				}
			}
		}
		notices.forEach(Runnable::run);
	}

	/**
	 * Tells whether an owner holds any service or watch, for checking that nothing is left behind.
	 *
	 * @param owner the owner
	 * @return whether anything of the owner is still registered
	 */
	public boolean holdsAnything(@NotNull String owner) {
		synchronized (lock) {
			return services.values().stream().flatMap(List::stream).anyMatch(entry -> entry.owner.equals(owner))
					|| watchers.values().stream().flatMap(List::stream).anyMatch(watcher -> watcher.owner.equals(owner));
		}
	}

	private List<Runnable> notices(ServiceKey<?> key, Entry entry, boolean registered) {
		List<Runnable> notices = new ArrayList<>();
		for (Watcher watcher : watchers.getOrDefault(key, List.of()))
			notices.add(() -> tell(watcher, key, entry, registered));

		return notices;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void tell(Watcher watcher, ServiceKey<?> key, Entry entry, boolean registered) {
		try {
			ServiceEvent event = ServiceEvent.builder()
					.key((ServiceKey) key)
					.service(entry.service)
					.owner(entry.owner)
					.priority(entry.priority)
					.registered(registered)
					.build();
			((Consumer) watcher.listener).accept(event);
		} catch (RuntimeException e) {
			errors.accept(e);
		}
	}

	private final class OwnedServices implements Services {
		private final String owner;

		private OwnedServices(String owner) {
			this.owner = owner;
		}

		@Override
		public <T> @NotNull ServiceRegistration register(@NotNull ServiceKey<T> key, @NotNull T service, int priority) {
			if (!key.getType().isInstance(service))
				throw new IllegalArgumentException(service.getClass().getName() + " is no " + key.getType().getName());

			Entry entry = new Entry(service, owner, priority, sequence.getAndIncrement());
			List<Runnable> notices;
			synchronized (lock) {
				List<Entry> entries = services.computeIfAbsent(key, ignored -> new ArrayList<>());
				entries.add(entry);
				entries.sort(Entry.ORDER);
				notices = notices(key, entry, true);
			}
			notices.forEach(Runnable::run);

			return new Registration(key, entry);
		}

		@Override
		public <T> @NotNull Optional<T> find(@NotNull ServiceKey<T> key) {
			synchronized (lock) {
				List<Entry> entries = services.getOrDefault(key, List.of());

				return entries.isEmpty() ? Optional.empty() : Optional.of(key.getType().cast(entries.getFirst().service));
			}
		}

		@Override
		public <T> @NotNull List<T> all(@NotNull ServiceKey<T> key) {
			synchronized (lock) {
				return services.getOrDefault(key, List.of()).stream().map(entry -> key.getType().cast(entry.service)).toList();
			}
		}

		@Override
		public <T> @NotNull AutoCloseable watch(@NotNull ServiceKey<T> key, @NotNull Consumer<ServiceEvent<T>> listener) {
			Watcher watcher = new Watcher(owner, listener);
			List<Entry> present;
			synchronized (lock) {
				watchers.computeIfAbsent(key, ignored -> new ArrayList<>()).add(watcher);
				present = List.copyOf(services.getOrDefault(key, List.of()));
			}
			present.forEach(entry -> tell(watcher, key, entry, true));

			return () -> {
				synchronized (lock) {
					watchers.getOrDefault(key, new ArrayList<>()).remove(watcher);
				}
			};
		}
	}

	private final class Registration implements ServiceRegistration {
		private final ServiceKey<?> key;
		private final Entry entry;

		private Registration(ServiceKey<?> key, Entry entry) {
			this.key = key;
			this.entry = entry;
		}

		@Override
		public void close() {
			List<Runnable> notices;
			synchronized (lock) {
				List<Entry> entries = services.get(key);
				if (entries == null || !entries.remove(entry)) return;
				notices = notices(key, entry, false);
			}
			notices.forEach(Runnable::run);
		}
	}

	private record Entry(Object service, String owner, int priority, long order) {
		static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::priority).reversed().thenComparingLong(Entry::order);
	}

	private record Watcher(String owner, Consumer<?> listener) {
	}
}
