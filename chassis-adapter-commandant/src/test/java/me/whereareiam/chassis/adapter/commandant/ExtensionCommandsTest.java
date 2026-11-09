package me.whereareiam.chassis.adapter.commandant;

import me.whereareiam.chassis.Extensions;
import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.type.ExtensionState;
import me.whereareiam.commandant.model.TranslatableDescription;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.VirtualComponent;
import net.kyori.adventure.text.VirtualComponentRenderer;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionCommandsTest {
	private final Map<String, InstalledExtension> installed = new LinkedHashMap<>();
	private final Set<String> refusing = new HashSet<>();
	private final List<String> answers = new ArrayList<>();
	private final List<String> changes = new ArrayList<>();

	@Test
	void listsEveryExtensionWithItsStateAndWhy() {
		put("monitoring", ExtensionState.ENABLED, List.of());
		put("shop", ExtensionState.WAITING, List.of("'gateway' ^0.2 (not installed)"));

		run(Set.of("arbor.command.module.list"), "arbor module list");

		assertEquals(List.of(
				"arbor.module.list.header(count=2)",
				"arbor.module.list.line(id='monitoring', version='0.1.0', state=arbor.module.state.enabled(), reason=text(''))",
				"arbor.module.list.line(id='shop', version='0.1.0', state=arbor.module.state.waiting(), reason=arbor.module.reason(why=''gateway' ^0.2 (not installed)'))",
				"arbor.module.list.footer()"
		), answers);
	}

	@Test
	void anEmptyListIsSaidSo() {
		run(Set.of("arbor.command.module.list"), "arbor module list");

		assertEquals(List.of("arbor.module.list.empty()"), answers);
	}

	@Test
	void infoGivesTheDescriptor() {
		put("monitoring", ExtensionState.ENABLED, List.of());

		run(Set.of("arbor.command.module.info"), "arbor module info monitoring");

		assertEquals(List.of("arbor.module.info(id='monitoring', version='0.1.0', state=arbor.module.state.enabled(), reason=text(''), "
				+ "name='monitoring', description='', authors='', jar='monitoring.jar')"), answers);
	}

	@Test
	void aChangeIsMadeThroughExtensionsAndAnUnknownIdIsSaidSo() {
		put("monitoring", ExtensionState.DISABLED, List.of());
		Set<String> all = Set.of("arbor.command.module.disable", "arbor.command.module.info");

		run(all, "arbor module disable monitoring");
		run(all, "arbor module info nothing");

		assertEquals(List.of("disable monitoring"), changes);
		assertEquals(List.of(
				"arbor.module.disabled(id='monitoring', version='0.1.0', state=arbor.module.state.disabled(), reason=text(''))",
				"arbor.module.unknown(id='nothing')"
		), answers);
	}

	@Test
	void aRefusedChangeSaysWhy() {
		put("shop", ExtensionState.WAITING, List.of());
		refusing.add("shop");

		run(Set.of("arbor.command.module.enable"), "arbor module enable shop");

		assertEquals(List.of("arbor.module.refused(id='shop', why='shop cannot start')"), answers);
	}

	@Test
	void aRescanCountsUnderTheHostsKeys() {
		put("monitoring", ExtensionState.ENABLED, List.of());

		run(Set.of("arbor.command.module.rescan"), "arbor module rescan", "texts.modules.");

		assertEquals(List.of("texts.modules.rescanned(count=1)"), answers);
	}

	@Test
	void eachCommandIsDescribedByAKey() {
		CommandManager<Set<String>> manager = run(Set.of("arbor.command.module.list"), "arbor module list");

		Map<String, String> descriptions = new TreeMap<>();
		manager.commands().forEach(command -> descriptions.put(
				command.components().get(2).name(),
				((TranslatableDescription) command.commandDescription().description()).getKey()
		));

		assertEquals(Map.of(
				"list", "arbor.module.description.list",
				"info", "arbor.module.description.info",
				"enable", "arbor.module.description.enable",
				"disable", "arbor.module.description.disable",
				"reload", "arbor.module.description.reload",
				"rescan", "arbor.module.description.rescan"
		), descriptions);
	}

	@Test
	void aSenderWithoutThePermissionIsRefused() {
		CompletionException failure = assertThrows(CompletionException.class, () -> run(Set.of(), "arbor module list"));

		assertInstanceOf(NoPermissionException.class, failure.getCause());
	}

	private CommandManager<Set<String>> run(Set<String> permissions, String command) {
		return run(permissions, command, "arbor.module.");
	}

	private CommandManager<Set<String>> run(Set<String> permissions, String command, String keys) {
		CommandManager<Set<String>> manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
			@Override
			public boolean hasPermission(@NotNull Set<String> sender, @NotNull String permission) {
				return permission.isEmpty() || sender.contains(permission);
			}
		};
		ExtensionCommands.builder(manager, this::extensions, "arbor", "module")
				.permissions(Set::contains)
				.replies((sender, message) -> answers.add(written(message)))
				.keys(keys)
				.arguments((name, value) -> Component.virtual(Void.class, new Named(name, value)))
				.build()
				.register();

		manager.commandExecutor().executeCommand(permissions, command).join();

		return manager;
	}

	/**
	 * Writes an answer as its key and its named arguments, a text value in quotes, so a count shows as a number.
	 */
	private static String written(Object value) {
		return switch (value) {
			case TranslatableComponent translatable -> translatable.key() + translatable.arguments().stream()
					.map(argument -> written(argument.value()))
					.collect(Collectors.joining(", ", "(", ")"));
			case VirtualComponent virtual when virtual.renderer() instanceof Named named -> named.name() + "=" + written(named.value());
			case TextComponent text -> "text('" + text.content() + "')";
			case String text -> "'" + text + "'";
			default -> String.valueOf(value);
		};
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
				if (refusing.contains(id)) throw new ExtensionException(id, id + " cannot start");
				changes.add(change + " " + id);

				return installed.get(id);
			}
		};
	}

	private record Named(String name, Object value) implements VirtualComponentRenderer<Void> {
		@Override
		public @NotNull ComponentLike apply(@NotNull Void context) {
			return Component.text(String.valueOf(value));
		}
	}
}
