package me.whereareiam.chassis.common.lifecycle;

import lombok.RequiredArgsConstructor;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.loading.ExtensionClassLoader;
import me.whereareiam.chassis.common.resolution.DependencyResolver;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts what can start: hands the undecided and waiting extensions to the {@link DependencyResolver} with the running
 * ones, fails or waits what it says, and starts the rest in its order through the {@link ExtensionLauncher}.
 *
 * @param <C> the host's context
 * @param <F> the type the host reads its own fields of a descriptor into
 */
@RequiredArgsConstructor
public final class ExtensionSettlement<C extends ExtensionContext, F> {
	private final @NotNull ExtensionEntries<C, F> entries;
	private final @NotNull DependencyResolver resolver;
	private final @NotNull ExtensionLauncher<C, F> launcher;

	/**
	 * Starts what can start now. A failure to start leaves the extensions that need it waiting, which the next pass
	 * decides, so it repeats until a pass starts nothing.
	 */
	public void settle() {
		boolean progressed = true;
		while (progressed) progressed = settleOnce();
	}

	private boolean settleOnce() {
		Map<String, ExtensionDescriptor> candidates = new LinkedHashMap<>();
		Map<String, ExtensionDescriptor> running = new LinkedHashMap<>();
		Map<String, String> unavailable = new LinkedHashMap<>();
		for (ExtensionEntries<C, F>.Entry entry : entries.all()) {
			switch (entry.current()) {
				case ENABLED -> running.put(entry.id(), entry.found.descriptor());
				case WAITING -> candidates.put(entry.id(), entry.found.descriptor());
				case DISABLED -> unavailable.put(entry.id(), "disabled");
				case FAILED -> unavailable.put(entry.id(), "failed");
			}
		}
		if (candidates.isEmpty()) return false;

		DependencyResolver.Resolution resolution = resolver.resolve(candidates, unavailable, running);
		resolution.failed().forEach((id, reason) -> entries.change(entries.entry(id), ExtensionState.FAILED, List.of(), new ExtensionException(id, reason)));
		resolution.waiting().forEach((id, reasons) -> entries.change(entries.entry(id), ExtensionState.WAITING, reasons, null));

		boolean started = false;
		for (String id : resolution.order()) started |= startWhenReady(entries.entry(id));

		return started;
	}

	// An earlier start in the same pass may have failed, so every required dependency is checked again.
	private boolean startWhenReady(ExtensionEntries<C, F>.Entry entry) {
		List<String> missing = missingDependencies(entry);
		if (missing.isEmpty()) return start(entry);

		entries.change(entry, ExtensionState.WAITING, missing, null);

		return false;
	}

	private List<String> missingDependencies(ExtensionEntries<C, F>.Entry entry) {
		List<String> missing = new ArrayList<>();
		for (ExtensionDescriptor.Dependency dependency : entry.found.descriptor().getDependencies()) {
			ExtensionEntries<C, F>.Entry target = entries.entry(dependency.getId());
			if (target != null && target.state == ExtensionState.ENABLED) continue;
			if (dependency.isOptional()) continue;

			String state = target == null || target.state == null ? "not installed" : target.state.name().toLowerCase();
			missing.add("'" + dependency.getId() + "' " + dependency.getVersion() + " (" + state + ")");
		}

		return missing;
	}

	private boolean start(ExtensionEntries<C, F>.Entry entry) {
		List<ExtensionClassLoader> dependencies = new ArrayList<>();
		for (ExtensionDescriptor.Dependency dependency : entry.found.descriptor().getDependencies()) {
			ExtensionEntries<C, F>.Entry target = entries.entry(dependency.getId());
			if (target != null && target.running != null) dependencies.add(target.running.loader());
		}

		try {
			entries.started(entry, launcher.start(entry.found.jar(), entry.found.descriptor(), entry.found.fields(), dependencies));

			return true;
		} catch (ExtensionException failure) {
			entries.change(entry, ExtensionState.FAILED, List.of(), failure);

			return false;
		}
	}
}
