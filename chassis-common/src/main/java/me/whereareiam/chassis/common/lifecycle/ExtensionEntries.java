package me.whereareiam.chassis.common.lifecycle;

import lombok.RequiredArgsConstructor;
import me.whereareiam.chassis.Extension;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.discovery.ExtensionDiscovery;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A host's installed extensions: each one's jar and descriptor, its state and what it waits for or why it failed, and
 * what runs, in the order it started. Each change of state goes to {@link ExtensionEvents}. It decides nothing:
 * {@link ExtensionSettlement} and {@link ExtensionControl} do, under the host's lock.
 *
 * @param <C> the host's context
 * @param <F> the type the host reads its own fields of a descriptor into
 */
@RequiredArgsConstructor
public final class ExtensionEntries<C extends ExtensionContext, F> {
	// The host's word for its extensions, for messages.
	private final @NotNull String kind;
	private final @NotNull ExtensionEvents events;

	// Every installed extension by id, in the order its jar was found; started ones also in start order.
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final List<String> started = new ArrayList<>();

	/**
	 * Returns an installed extension as administrators see it.
	 *
	 * @param id the extension's id
	 * @return the extension
	 * @throws ExtensionException when no extension of the id is installed
	 */
	public @NotNull InstalledExtension require(@NotNull String id) throws ExtensionException {
		return installed(id).view();
	}

	/**
	 * Finds an installed extension as administrators see it.
	 *
	 * @param id the extension's id
	 * @return the extension, or empty when none of the id is installed
	 */
	public @NotNull Optional<InstalledExtension> find(@NotNull String id) {
		return Optional.ofNullable(entries.get(id)).map(Entry::view);
	}

	/**
	 * Returns every installed extension as administrators see it: the running ones in the order they started, then
	 * the others in the order their jars were found.
	 *
	 * @return the views
	 */
	public @NotNull List<InstalledExtension> list() {
		List<InstalledExtension> listed = new ArrayList<>();
		for (String id : started) listed.add(entries.get(id).view());

		for (Entry entry : entries.values())
			if (!started.contains(entry.id())) listed.add(entry.view());

		return listed;
	}

	/**
	 * Returns the host's own fields of an installed extension's descriptor.
	 *
	 * @param id the extension's id
	 * @return the fields, or empty when none of the id is installed, its descriptor could not be read, or the host
	 * reads no fields
	 */
	public @NotNull Optional<F> fields(@NotNull String id) {
		return Optional.ofNullable(entries.get(id)).map(entry -> entry.found.fields());
	}

	/**
	 * Returns the class loader of a running extension.
	 *
	 * @param id the extension's id
	 * @return its class loader, or empty when it does not run
	 */
	public @NotNull Optional<ClassLoader> loader(@NotNull String id) {
		return running(id).map(ExtensionLauncher.Running::loader);
	}

	/**
	 * Returns the instance of a running extension's main class.
	 *
	 * @param id the extension's id
	 * @return the instance, or empty when it does not run
	 */
	public @NotNull Optional<Extension<C>> instance(@NotNull String id) {
		return running(id).map(ExtensionLauncher.Running::instance);
	}

	private Optional<ExtensionLauncher.Running<C>> running(String id) {
		return Optional.ofNullable(entries.get(id)).map(entry -> entry.running);
	}

	@Nullable Entry entry(@NotNull String id) {
		return entries.get(id);
	}

	@NotNull Entry installed(@NotNull String id) throws ExtensionException {
		Entry entry = entries.get(id);
		if (entry == null) throw new ExtensionException(id, "No " + kind + " '" + id + "' is installed");

		return entry;
	}

	@NotNull List<Entry> all() {
		return List.copyOf(entries.values());
	}

	// The ids of the running extensions, in the order they started.
	@NotNull List<String> started() {
		return List.copyOf(started);
	}

	/**
	 * Adds a jar the discovery found. A second jar of an id already installed fails and stays apart under its file
	 * name; a jar whose descriptor could not be read fails, and one an administrator turned off is disabled.
	 *
	 * @param found    the jar
	 * @param disabled the ids an administrator turned off
	 */
	void install(@NotNull ExtensionDiscovery.Found<F> found, @NotNull Set<String> disabled) {
		if (entries.containsKey(found.id())) found = duplicate(found);
		String id = found.id();
		if (entries.containsKey(id)) return;

		Entry entry = new Entry(found);
		entries.put(id, entry);
		if (found.descriptor() == null) {
			change(entry, ExtensionState.FAILED, List.of(), found.failure());
			return;
		}

		if (disabled.contains(id)) change(entry, ExtensionState.DISABLED, List.of(), null);
	}

	// A second jar of an installed id, failed under its file name.
	private ExtensionDiscovery.Found<F> duplicate(ExtensionDiscovery.Found<F> found) {
		String id = found.id();
		String jar = found.jar().getFileName().toString();
		String earlier = entries.get(id).found.jar().getFileName().toString();

		return ExtensionDiscovery.Found.failed(found.jar(),
				new ExtensionException(id, "Both " + earlier + " and " + jar + " are '" + id + "'; " + jar + " is left out"));
	}

	void remove(@NotNull Entry entry) {
		entries.remove(entry.id());
		started.remove(entry.id());
	}

	void started(@NotNull Entry entry, @NotNull ExtensionLauncher.Running<C> running) {
		entry.running = running;
		started.add(entry.id());
		change(entry, ExtensionState.ENABLED, List.of(), null);
	}

	void stopped(@NotNull Entry entry, @NotNull ExtensionState next, @NotNull List<String> waitingFor) {
		entry.running = null;
		started.remove(entry.id());
		change(entry, next, waitingFor, null);
	}

	/**
	 * Changes an extension's state and tells it. Nothing is told when neither the state nor what it waits for changed.
	 *
	 * @param entry      its entry
	 * @param next       the new state
	 * @param waitingFor what it waits for, when it waits
	 * @param failure    why it failed, when it failed
	 */
	void change(@NotNull Entry entry, @NotNull ExtensionState next, @NotNull List<String> waitingFor, @Nullable Throwable failure) {
		ExtensionState previous = entry.state;
		boolean same = previous == next && entry.waitingFor.equals(waitingFor);

		entry.state = next;
		entry.waitingFor = List.copyOf(waitingFor);
		entry.failure = failure;
		if (same) return;

		events.changed(entry.view(), previous);
	}

	/**
	 * One installed extension.
	 */
	final class Entry {
		ExtensionDiscovery.Found<F> found;
		// null: installed and not yet decided.
		@Nullable ExtensionState state;
		@NotNull List<String> waitingFor = List.of();
		@Nullable Throwable failure;
		ExtensionLauncher.@Nullable Running<C> running;

		private Entry(ExtensionDiscovery.Found<F> found) {
			this.found = found;
		}

		@NotNull String id() {
			return found.id();
		}

		// Makes it undecided again, for the next pass to decide.
		void candidate() {
			state = null;
			waitingFor = List.of();
			failure = null;
		}

		// Its state as administrators see it: an undecided extension waits.
		@NotNull ExtensionState current() {
			return state == null ? ExtensionState.WAITING : state;
		}

		@NotNull InstalledExtension view() {
			return InstalledExtension.builder()
					.id(id())
					.descriptor(found.descriptor())
					.jar(found.jar())
					.state(current())
					.waitingFor(waitingFor)
					.failure(failure)
					.build();
		}
	}
}
