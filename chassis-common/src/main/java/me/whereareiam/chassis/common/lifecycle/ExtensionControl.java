package me.whereareiam.chassis.common.lifecycle;

import lombok.RequiredArgsConstructor;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.discovery.ExtensionDiscovery;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What an administrator does to installed extensions: finding new jars, enabling, disabling, reloading and looking at
 * them again, and stopping them, dependents first. The host keeps the selection file and calls this under its lock.
 *
 * @param <C> the host's context
 * @param <F> the type the host reads its own fields of a descriptor into
 */
@RequiredArgsConstructor
public final class ExtensionControl<C extends ExtensionContext, F> {
	private final @NotNull ExtensionEntries<C, F> entries;
	private final @NotNull ExtensionDiscovery<F> discovery;
	private final @NotNull ExtensionLauncher<C, F> launcher;
	private final @NotNull ExtensionSettlement<C, F> settlement;
	private final @NotNull ExtensionEvents events;

	/**
	 * Installs every jar of the folder not installed yet.
	 *
	 * @param disabled the ids an administrator turned off
	 * @throws IOException when the folder cannot be made or listed
	 */
	public void install(@NotNull Set<String> disabled) throws IOException {
		Set<Path> known = new LinkedHashSet<>();
		entries.all().forEach(entry -> known.add(entry.found.jar()));

		for (ExtensionDiscovery.Found<F> found : discovery.discover())
			if (!known.contains(found.jar())) entries.install(found, disabled);
	}

	/**
	 * Enables an extension an administrator turned on: reads its jar again when its descriptor could not be read, and
	 * starts it when it can start.
	 *
	 * @param id the extension's id
	 * @return the extension as it is now
	 * @throws ExtensionException when no extension of the id is installed
	 */
	public @NotNull InstalledExtension enable(@NotNull String id) throws ExtensionException {
		ExtensionEntries<C, F>.Entry entry = entries.installed(id);
		if (entry.state == ExtensionState.ENABLED) return entry.view();

		if (entry.found.descriptor() == null) entry.found = discovery.read(entry.found.jar());
		entry.candidate();
		settlement.settle();

		return entry.view();
	}

	/**
	 * Disables an extension an administrator turned off, after the extensions that depend on it, which wait.
	 *
	 * @param id the extension's id
	 * @return the extension as it is now
	 * @throws ExtensionException when no extension of the id is installed
	 */
	public @NotNull InstalledExtension disable(@NotNull String id) throws ExtensionException {
		ExtensionEntries<C, F>.Entry entry = entries.installed(id);

		stopDependents(id, "disabled");
		stop(entry, ExtensionState.DISABLED, List.of());
		entries.change(entry, ExtensionState.DISABLED, List.of(), null);
		settlement.settle();

		return entry.view();
	}

	/**
	 * Reloads an extension from its jar: stops it and its dependents, reads the jar again and starts what can start.
	 *
	 * @param id the extension's id
	 * @return the extension as it is now
	 * @throws ExtensionException when no extension of the id is installed, its jar is gone, or the jar is now another
	 *                            extension
	 */
	public @NotNull InstalledExtension reload(@NotNull String id) throws ExtensionException {
		ExtensionEntries<C, F>.Entry entry = entries.installed(id);
		List<ExtensionEntries<C, F>.Entry> dependents = stopDependents(id, "reloading");
		stop(entry, ExtensionState.WAITING, List.of("its own reload"));

		Path jar = entry.found.jar();
		if (!Files.isRegularFile(jar)) {
			entries.remove(entry);
			throw new ExtensionException(id, jar.getFileName() + " is gone; '" + id + "' is no longer installed");
		}

		ExtensionDiscovery.Found<F> reread = discovery.read(jar);
		if (reread.descriptor() != null && !reread.id().equals(id)) {
			entries.remove(entry);
			entries.install(reread, Set.of());
			settlement.settle();
			throw new ExtensionException(id, jar.getFileName() + " is now the extension '" + reread.id() + "'");
		}

		entry.found = reread;
		if (entry.state != ExtensionState.DISABLED) entry.candidate();
		dependents.forEach(ExtensionEntries.Entry::candidate);
		settlement.settle();

		return entry.view();
	}

	/**
	 * Looks at every extension that does not run again, then starts what can start: one whose jar is gone is
	 * forgotten, one whose descriptor could not be read is read again, and one that waits is decided again.
	 */
	public void revisit() {
		for (ExtensionEntries<C, F>.Entry entry : entries.all()) revisit(entry);

		settlement.settle();
	}

	/**
	 * Stops every running extension, the last started first.
	 *
	 * @param waitingFor what each waits for then
	 */
	public void stopAll(@NotNull List<String> waitingFor) {
		events.whileStopping(() -> {
			for (String id : entries.started().reversed()) stop(entries.entry(id), ExtensionState.WAITING, waitingFor);
		});
	}

	private void revisit(ExtensionEntries<C, F>.Entry entry) {
		if (entry.state == ExtensionState.ENABLED) return;
		if (!Files.isRegularFile(entry.found.jar())) {
			entries.remove(entry);
			return;
		}

		if (entry.found.descriptor() == null && entry.state == ExtensionState.FAILED) reread(entry);
		if (entry.state == ExtensionState.WAITING) entry.candidate();
	}

	private void reread(ExtensionEntries<C, F>.Entry entry) {
		entry.found = discovery.read(entry.found.jar());
		if (entry.found.descriptor() != null) {
			entry.candidate();
			return;
		}

		entries.change(entry, ExtensionState.FAILED, List.of(), entry.found.failure());
	}

	// Stops the enabled extensions that depend on the given one, directly or not, the last started first.
	private List<ExtensionEntries<C, F>.Entry> stopDependents(String id, String word) {
		Set<String> affected = dependentsOf(id);
		List<ExtensionEntries<C, F>.Entry> stopped = new ArrayList<>();
		for (String candidate : entries.started().reversed()) {
			if (!affected.contains(candidate)) continue;

			ExtensionEntries<C, F>.Entry entry = entries.entry(candidate);
			stop(entry, ExtensionState.WAITING, reasons(entry.found.descriptor(), id, word, affected));
			stopped.add(entry);
		}

		return stopped;
	}

	// The running extensions that depend on the given one, directly or not.
	private Set<String> dependentsOf(String id) {
		Set<String> affected = new LinkedHashSet<>(List.of(id));
		boolean grew = true;
		while (grew) {
			grew = false;
			for (String candidate : entries.started())
				if (!affected.contains(candidate) && dependsOnAny(candidate, affected)) grew = affected.add(candidate);
		}

		affected.remove(id);

		return affected;
	}

	private boolean dependsOnAny(String id, Set<String> ids) {
		return entries.entry(id).found.descriptor().getDependencies().stream().anyMatch(dependency -> ids.contains(dependency.getId()));
	}

	// Why a stopped dependent waits, as the resolver would say it: for the given one by the word for what happens to
	// it, for the other stopped ones as waiting.
	private static List<String> reasons(ExtensionDescriptor dependent, String id, String word, Set<String> affected) {
		List<String> reasons = new ArrayList<>();
		for (ExtensionDescriptor.Dependency dependency : dependent.getDependencies()) {
			if (dependency.getId().equals(id)) reasons.add("'" + id + "' " + dependency.getVersion() + " (" + word + ")");
			if (affected.contains(dependency.getId())) reasons.add("'" + dependency.getId() + "' " + dependency.getVersion() + " (waiting)");
		}

		return reasons;
	}

	private void stop(ExtensionEntries<C, F>.Entry entry, ExtensionState next, List<String> waitingFor) {
		if (entry.running == null) return;

		launcher.stop(entry.id(), entry.running);
		entries.stopped(entry, next, waitingFor);
	}
}
