package me.whereareiam.chassis.adapter.attache;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.launcher.AttacheLauncher;
import me.whereareiam.chassis.ExtensionPreparation;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * An Attache manager for one load of one extension: it adds libraries to the extension's loader and asks that loader
 * whether a library is already there.
 */
final class ExtensionLibraryManager extends BaseLibraryManager {
	private final @NotNull ExtensionPreparation.Loader loader;

	ExtensionLibraryManager(@NotNull LoggingHelper logging, @NotNull Path folder, @NotNull ExtensionPreparation.Loader loader) {
		super(logging, folder, ".", new AttacheLauncher());
		this.loader = loader;
	}

	@Override
	protected void addToClasspath(@NotNull Path file) {
		loader.addJar(file);
	}

	@Override
	protected @NotNull ClassLoader getDescriptorClassLoader() {
		return loader.classLoader();
	}
}
