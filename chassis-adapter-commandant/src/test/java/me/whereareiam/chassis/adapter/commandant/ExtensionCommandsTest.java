package me.whereareiam.chassis.adapter.commandant;

import me.whereareiam.chassis.Extensions;
import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.type.ExtensionState;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionCommandsTest {
	private final Map<String, InstalledExtension> installed = new LinkedHashMap<>();
	private final List<String> answers = new ArrayList<>();
	private final List<String> changes = new ArrayList<>();

	@Test
	void listsEveryExtensionWithItsStateAndWhy() throws Exception {
		put("monitoring", ExtensionState.ENABLED, List.of());
		put("shop", ExtensionState.WAITING, List.of("'gateway' ^0.2 (not installed)"));

		run(Set.of("arbor.command.module.list"), "arbor module list");

		assertEquals(List.of(
				"2 modules:",
				"  monitoring 0.1.0 running",
				"  shop 0.1.0 waiting: 'gateway' ^0.2 (not installed)"
		), answers);
	}

	@Test
	void aChangeIsMadeThroughExtensionsAndAnUnknownIdIsSaidSo() throws Exception {
		put("monitoring", ExtensionState.ENABLED, List.of());
		Set<String> all = Set.of("arbor.command.module.disable", "arbor.command.module.info");

		run(all, "arbor module disable monitoring");
		run(all, "arbor module info nothing");

		assertEquals(List.of("disable monitoring"), changes);
		assertEquals(List.of(
				"The module monitoring is off; it stays off across restarts.",
				"No module nothing is installed."
		), answers);
	}

	@Test
	void aSenderWithoutThePermissionIsRefused() {
		CompletionException failure = assertThrows(CompletionException.class, () -> run(Set.of(), "arbor module list"));

		assertInstanceOf(NoPermissionException.class, failure.getCause());
	}

	private void run(Set<String> permissions, String command) {
		CommandManager<Set<String>> manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
			@Override
			public boolean hasPermission(@NotNull Set<String> sender, @NotNull String permission) {
				return permission.isEmpty() || sender.contains(permission);
			}
		};
		ExtensionCommands.builder(manager, this::extensions, "arbor", "module")
				.permissions(Set::contains)
				.replies((sender, message) -> answers.addAll(List.of(PlainTextComponentSerializer.plainText().serialize(message).split("\n"))))
				.build()
				.register();

		manager.commandExecutor().executeCommand(permissions, command).join();
	}

	private void put(String id, ExtensionState state, List<String> waitingFor) {
		installed.put(id, InstalledExtension.builder()
				.id(id)
				.descriptor(ExtensionDescriptor.builder().id(id).name(id).version(Version.parse("0.1.0")).main("x." + id).build())
				.jar(Path.of(id + ".jar"))
				.state(state)
				.waitingFor(waitingFor)
				.build());
	}

	private Extensions extensions() {
		return new Extensions() {
			@Override
			public @NotNull List<InstalledExtension> list() {
				return List.copyOf(installed.values());
			}

			@Override
			public @NotNull Optional<InstalledExtension> find(@NotNull String id) {
				return Optional.ofNullable(installed.get(id));
			}

			@Override
			public @NotNull InstalledExtension enable(@NotNull String id) throws ExtensionException {
				return changed("enable", id);
			}

			@Override
			public @NotNull InstalledExtension disable(@NotNull String id) throws ExtensionException {
				return changed("disable", id);
			}

			@Override
			public @NotNull InstalledExtension reload(@NotNull String id) throws ExtensionException {
				return changed("reload", id);
			}

			@Override
			public @NotNull List<InstalledExtension> rescan() {
				return list();
			}

			@Override
			public @NotNull AutoCloseable subscribe(@NotNull Consumer<ExtensionEvent> listener) {
				return () -> {
				};
			}

			private InstalledExtension changed(String change, String id) throws ExtensionException {
				if (!installed.containsKey(id)) throw new ExtensionException(id, "No module '" + id + "' is installed");
				changes.add(change + " " + id);

				return installed.get(id);
			}
		};
	}
}
