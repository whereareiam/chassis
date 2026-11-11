package me.whereareiam.chassis.adapter.attache;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.service.Services;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * A host's context that hands an extension its library groups.
 */
public final class GroupedContext implements ExtensionContext {
	private final ExtensionContext base;
	private final LibraryGroups groups;

	GroupedContext(ExtensionContext base, LibraryGroups groups) {
		this.base = base;
		this.groups = groups;
	}

	public LibraryGroups groups() {
		return groups;
	}

	@Override public @NotNull ExtensionDescriptor descriptor() { return base.descriptor(); }
	@Override public @NotNull Path dataFolder() { return base.dataFolder(); }
	@Override public @NotNull System.Logger logger() { return base.logger(); }
	@Override public @NotNull Services services() { return base.services(); }
	@Override public void onClose(@NotNull AutoCloseable resource) { base.onClose(resource); }
}
