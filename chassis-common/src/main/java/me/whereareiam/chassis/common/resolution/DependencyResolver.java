package me.whereareiam.chassis.common.resolution;

import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.model.VersionRange;
import me.whereareiam.chassis.platform.Platform;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which extensions can start and in which order: what fits the host and the platform, what waits for a
 * dependency or a plugin, and the order that starts every dependency before the extensions that need it.
 */
public final class DependencyResolver {
	private final @NotNull Version hostVersion;
	private final @NotNull Map<String, Version> offered;
	private final @NotNull Platform platform;

	/**
	 * Makes the resolver.
	 *
	 * @param hostVersion the version of the host's API, which descriptors' {@code host} must accept
	 * @param offered     what the host offers for descriptors' {@code requires}, by name, with its version
	 * @param platform    the platform the host runs on
	 */
	public DependencyResolver(@NotNull Version hostVersion, @NotNull Map<String, Version> offered, @NotNull Platform platform) {
		this.hostVersion = hostVersion;
		this.offered = Map.copyOf(offered);
		this.platform = platform;
	}

	/**
	 * Resolves the candidates: the extensions that are neither turned off nor failed.
	 *
	 * @param candidates  the extensions that may start, by id
	 * @param unavailable the other installed extensions, by id, each with the word for why it cannot serve as a
	 *                    dependency now (such as {@code disabled})
	 * @param running     the extensions that run already; they count as resolved and keep running
	 * @return the resolution
	 */
	public @NotNull Resolution resolve(
			@NotNull Map<String, ExtensionDescriptor> candidates,
			@NotNull Map<String, String> unavailable,
			@NotNull Map<String, ExtensionDescriptor> running
	) {
		Map<String, String> failed = new LinkedHashMap<>();
		Map<String, List<String>> waiting = new LinkedHashMap<>();
		Map<String, ExtensionDescriptor> pending = new LinkedHashMap<>();

		for (ExtensionDescriptor descriptor : candidates.values()) {
			String unfit = unfit(descriptor);
			if (unfit != null) failed.put(descriptor.getId(), unfit);
			else pending.put(descriptor.getId(), descriptor);
		}

		boolean changed = true;
		while (changed) {
			changed = false;
			for (ExtensionDescriptor descriptor : List.copyOf(pending.values())) {
				List<String> reasons = waitingFor(descriptor, pending, running, unavailable, failed, waiting);
				if (reasons.isEmpty()) continue;

				pending.remove(descriptor.getId());
				waiting.put(descriptor.getId(), reasons);
				changed = true;
			}
		}

		List<String> order = order(pending, running, failed);

		return new Resolution(order, waiting, failed);
	}

	private String unfit(ExtensionDescriptor descriptor) {
		if (!descriptor.getHost().accepts(hostVersion))
			return "It is built for the host's API " + descriptor.getHost() + ", and the host's API is " + hostVersion;
		String unfitPlatform = unfitPlatform(descriptor);
		if (unfitPlatform != null) return unfitPlatform;
		for (Map.Entry<String, VersionRange> required : descriptor.getRequires().entrySet()) {
			Version version = offered.get(required.getKey());
			if (version == null) return "It requires '" + required.getKey() + "', which the host does not offer";
			if (!required.getValue().accepts(version))
				return "It requires '" + required.getKey() + "' " + required.getValue() + ", and the host offers " + version;
		}

		return null;
	}

	private String unfitPlatform(ExtensionDescriptor descriptor) {
		if (descriptor.getPlatforms().isEmpty()) return null;

		List<ExtensionDescriptor.SupportedPlatform> named = descriptor.getPlatforms().stream()
				.filter(supported -> supported.getName().equals(platform.name()))
				.toList();
		String listed = String.join(", ", descriptor.getPlatforms().stream().map(Object::toString).toList());
		if (named.isEmpty()) return "It runs on " + listed + ", not on " + platform.name();

		Version version = platform.version();
		for (ExtensionDescriptor.SupportedPlatform supported : named) {
			if (supported.getVersion().equals(VersionRange.ANY)) return null;
			if (version != null && supported.getVersion().accepts(version)) return null;
		}

		return "It runs on " + listed + ", not on " + platform.name() + " " + (version == null ? "of an unknown version" : version);
	}

	private List<String> waitingFor(
			ExtensionDescriptor descriptor,
			Map<String, ExtensionDescriptor> pending,
			Map<String, ExtensionDescriptor> running,
			Map<String, String> unavailable,
			Map<String, String> failed,
			Map<String, List<String>> waiting
	) {
		List<String> reasons = new ArrayList<>();
		for (String plugin : descriptor.getPlugins())
			if (!platform.hasPlugin(plugin)) reasons.add("the plugin '" + plugin + "', which is not loaded");

		for (ExtensionDescriptor.Dependency dependency : descriptor.getDependencies()) {
			ExtensionDescriptor target = pending.get(dependency.getId());
			if (target == null) target = running.get(dependency.getId());
			if (target != null) {
				if (!dependency.getVersion().accepts(target.getVersion()))
					reasons.add("'" + dependency.getId() + "' " + dependency.getVersion() + ", and " + target.getVersion() + " is installed");
				continue;
			}
			if (dependency.isOptional()) continue;

			String why = unavailable.get(dependency.getId());
			if (why == null && failed.containsKey(dependency.getId())) why = "failed";
			if (why == null && waiting.containsKey(dependency.getId())) why = "waiting";
			if (why == null) why = "not installed";
			reasons.add("'" + dependency.getId() + "' " + dependency.getVersion() + " (" + why + ")");
		}

		return reasons;
	}

	private List<String> order(Map<String, ExtensionDescriptor> pending, Map<String, ExtensionDescriptor> running, Map<String, String> failed) {
		List<String> order = new ArrayList<>();
		Map<String, Integer> marks = new HashMap<>();
		for (String id : pending.keySet())
			visit(id, pending, running, marks, new LinkedHashSet<>(), order, failed);

		return order;
	}

	// Depth first: 1 while on the path, 2 when placed; a node met again while on the path closes a cycle.
	private boolean visit(
			String id,
			Map<String, ExtensionDescriptor> pending,
			Map<String, ExtensionDescriptor> running,
			Map<String, Integer> marks,
			LinkedHashSet<String> path,
			List<String> order,
			Map<String, String> failed
	) {
		Integer mark = marks.get(id);
		if (mark != null && mark == 2) return !failed.containsKey(id);
		if (mark != null) {
			List<String> cycle = new ArrayList<>(path).subList(new ArrayList<>(path).indexOf(id), path.size());
			for (String member : cycle) failed.put(member, "Its dependencies form a cycle: " + String.join(" -> ", cycle) + " -> " + id);

			return false;
		}

		marks.put(id, 1);
		path.add(id);
		boolean placeable = true;
		for (ExtensionDescriptor.Dependency dependency : pending.get(id).getDependencies()) {
			if (running.containsKey(dependency.getId()) || !pending.containsKey(dependency.getId())) continue;
			if (!visit(dependency.getId(), pending, running, marks, path, order, failed)) placeable = false;
		}
		path.remove(id);
		marks.put(id, 2);
		if (!placeable && !failed.containsKey(id)) failed.put(id, "A dependency of it failed to resolve");
		if (failed.containsKey(id)) return false;

		order.add(id);

		return true;
	}

	/**
	 * What the resolver decided.
	 *
	 * @param order   the extensions that can start, each after its dependencies
	 * @param waiting the extensions that wait, with what each waits for
	 * @param failed  the extensions that cannot start, with why
	 */
	public record Resolution(@NotNull List<String> order, @NotNull Map<String, List<String>> waiting, @NotNull Map<String, String> failed) {
	}
}
