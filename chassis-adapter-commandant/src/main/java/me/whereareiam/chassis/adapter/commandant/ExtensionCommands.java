package me.whereareiam.chassis.adapter.commandant;

import me.whereareiam.chassis.Extensions;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.commandant.model.TranslatableDescription;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.permission.PredicatePermission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * The commands that control a host's extensions, mounted under the host's root command with its kind word:
 *
 * <pre>
 * /&lt;root&gt; &lt;kind&gt; list               every installed extension and its state
 * /&lt;root&gt; &lt;kind&gt; info &lt;id&gt;          its descriptor, state, and why it waits or failed
 * /&lt;root&gt; &lt;kind&gt; enable &lt;id&gt;        turns it on, kept across restarts, and starts it
 * /&lt;root&gt; &lt;kind&gt; disable &lt;id&gt;       turns it off; its dependents wait
 * /&lt;root&gt; &lt;kind&gt; reload &lt;id&gt;        loads it again from its jar
 * /&lt;root&gt; &lt;kind&gt; rescan             installs jars added since, starts what can start
 * </pre>
 *
 * <pre>{@code
 * ExtensionCommands.builder(manager, () -> modules, "arbor", "module")
 *         .permissions((sender, permission) -> sender.hasPermission(permission))
 *         .replies(Sender::sendMessage)
 *         .keys("arbor.module.")
 *         .arguments(TextArgument::of)
 *         .changes(scheduler.async())
 *         .build()
 *         .register();
 * }</pre>
 *
 * <p>Each command needs the permission {@code <root>.command.<kind>.<name>}, such as {@code arbor.command.module.list},
 * which the host checks itself. A change (enable, disable, reload, rescan) runs on the executor the host gives, since
 * starting or stopping an extension may take a while, and its answer is sent from there. Each command carries
 * Commandant's definition id {@code <kind>-<name>}, so a host can word, alias or hide it with Commandant's
 * definitions.</p>
 *
 * <p>Every answer is a {@link Component#translatable(String, ComponentLike...) translatable component}: its key is the
 * host's prefix followed by the message, and its arguments are named by the host's
 * {@link Builder#arguments(BiFunction) argument factory}, so the host's translator words it in each sender's
 * language. The host writes these messages in its translations; Chassis ships no wording:</p>
 *
 * <table>
 *   <caption>Messages and their arguments</caption>
 *   <tr><th>Message</th><th>Arguments</th><th>Sent</th></tr>
 *   <tr><td>{@code list.header}</td><td>{@code count}</td><td>above the list</td></tr>
 *   <tr><td>{@code list.line}</td><td>{@code id}, {@code version}, {@code state}, {@code reason}</td><td>for each installed extension</td></tr>
 *   <tr><td>{@code list.footer}</td><td></td><td>below the list</td></tr>
 *   <tr><td>{@code list.empty}</td><td></td><td>instead of the list, when none is installed</td></tr>
 *   <tr><td>{@code info}</td><td>{@code id}, {@code version}, {@code state}, {@code reason}, {@code name}, {@code description}, {@code authors}, {@code jar}</td><td>for {@code info}</td></tr>
 *   <tr><td>{@code enabled}</td><td>{@code id}, {@code version}, {@code state}, {@code reason}</td><td>after {@code enable}</td></tr>
 *   <tr><td>{@code disabled}</td><td>{@code id}, {@code version}, {@code state}, {@code reason}</td><td>after {@code disable}</td></tr>
 *   <tr><td>{@code reloaded}</td><td>{@code id}, {@code version}, {@code state}, {@code reason}</td><td>after {@code reload}</td></tr>
 *   <tr><td>{@code rescanned}</td><td>{@code count}</td><td>after {@code rescan}</td></tr>
 *   <tr><td>{@code unknown}</td><td>{@code id}</td><td>when no extension has the id</td></tr>
 *   <tr><td>{@code refused}</td><td>{@code id}, {@code why}</td><td>when a change could not be made</td></tr>
 *   <tr><td>{@code state.enabled}, {@code state.disabled}, {@code state.waiting}, {@code state.failed}</td><td></td><td>as the value of {@code state}</td></tr>
 *   <tr><td>{@code reason}</td><td>{@code why}</td><td>as the value of {@code reason} of a waiting or failed extension; an empty component otherwise</td></tr>
 *   <tr><td>{@code description.list}, {@code description.info}, {@code description.enable}, {@code description.disable}, {@code description.reload}, {@code description.rescan}</td><td></td><td>as each command's description, which Commandant's help shows</td></tr>
 * </table>
 *
 * <p>{@code count} is an {@link Integer}; {@code state} and {@code reason} are translatable components; every other
 * value is text: {@code why} is what the extension waits for, why it failed, or why a change was refused, as Chassis
 * words it; {@code authors} is empty when the descriptor names none.</p>
 *
 * @param <S> the host's sender
 */
public final class ExtensionCommands<S> {
	private final @NotNull CommandManager<S> manager;
	private final @NotNull Supplier<Extensions> extensions;
	private final @NotNull String root;
	private final @NotNull String kind;
	private final @NotNull BiPredicate<S, String> permissions;
	private final @NotNull BiConsumer<S, Component> replies;
	private final @NotNull String keys;
	private final @NotNull BiFunction<String, Object, ComponentLike> arguments;
	private final @NotNull Executor changes;

	private ExtensionCommands(Builder<S> builder) {
		this.manager = builder.manager;
		this.extensions = builder.extensions;
		this.root = builder.root;
		this.kind = builder.kind;
		this.permissions = builder.permissions;
		this.replies = builder.replies;
		this.keys = builder.keys;
		this.arguments = builder.arguments;
		this.changes = builder.changes;
	}

	/**
	 * Starts the commands.
	 *
	 * @param manager    the host's Cloud command manager
	 * @param extensions the host's extensions, asked at every command, so that the host may start them later
	 * @param root       the host's root command, such as {@code arbor}
	 * @param kind       the host's word for its extensions, such as {@code module}
	 * @param <S>        the host's sender
	 * @return the builder
	 */
	public static <S> @NotNull Builder<S> builder(
			@NotNull CommandManager<S> manager,
			@NotNull Supplier<Extensions> extensions,
			@NotNull String root,
			@NotNull String kind
	) {
		return new Builder<>(manager, extensions, root, kind);
	}

	/**
	 * Registers the six commands with the manager.
	 */
	public void register() {
		manager.command(command("list").handler(context -> list(context.sender())));
		manager.command(command("info")
				.required("id", stringParser(), ids())
				.handler(context -> info(context.sender(), context.get("id"))));
		manager.command(command("enable")
				.required("id", stringParser(), ids())
				.handler(context -> change(context.sender(), context.get("id"), Change.ENABLE)));
		manager.command(command("disable")
				.required("id", stringParser(), ids())
				.handler(context -> change(context.sender(), context.get("id"), Change.DISABLE)));
		manager.command(command("reload")
				.required("id", stringParser(), ids())
				.handler(context -> change(context.sender(), context.get("id"), Change.RELOAD)));
		manager.command(command("rescan").handler(context -> rescan(context.sender())));
	}

	private Command.Builder<S> command(String name) {
		String permission = root + ".command." + kind + "." + name;

		return manager.commandBuilder(root)
				.literal(kind)
				.literal(name)
				.commandDescription(TranslatableDescription.of(keys + "description." + name))
				.meta(CommandantKeys.DEFINITION_ID, kind + "-" + name)
				.permission(PredicatePermission.<S>of(sender -> permissions.test(sender, permission)));
	}

	private SuggestionProvider<S> ids() {
		return SuggestionProvider.blockingStrings((context, input) -> extensions.get().list().stream().map(InstalledExtension::getId).toList());
	}

	private void list(S sender) {
		List<InstalledExtension> installed = extensions.get().list();
		if (installed.isEmpty()) {
			reply(sender, "list.empty");
			return;
		}

		reply(sender, "list.header", argument("count", installed.size()));
		for (InstalledExtension extension : installed) reply(sender, "list.line", described(extension));
		reply(sender, "list.footer");
	}

	private void info(S sender, String id) {
		extensions.get().find(id).ifPresentOrElse(extension -> {
			ExtensionDescriptor descriptor = extension.getDescriptor();
			reply(sender, "info", described(extension,
					argument("name", descriptor == null ? extension.getId() : descriptor.getName()),
					argument("description", descriptor == null || descriptor.getDescription() == null ? "" : descriptor.getDescription()),
					argument("authors", descriptor == null ? "" : String.join(", ", descriptor.getAuthors())),
					argument("jar", extension.getJar().getFileName().toString())));
		}, () -> reply(sender, "unknown", argument("id", id)));
	}

	private void change(S sender, String id, Change change) {
		changes.execute(() -> {
			try {
				InstalledExtension extension = switch (change) {
					case ENABLE -> extensions.get().enable(id);
					case DISABLE -> extensions.get().disable(id);
					case RELOAD -> extensions.get().reload(id);
				};
				String message = switch (change) {
					case ENABLE -> "enabled";
					case DISABLE -> "disabled";
					case RELOAD -> "reloaded";
				};
				reply(sender, message, described(extension));
			} catch (ExtensionException e) {
				if (extensions.get().find(id).isEmpty()) reply(sender, "unknown", argument("id", id));
				else reply(sender, "refused", argument("id", id), argument("why", String.valueOf(e.getMessage())));
			}
		});
	}

	private void rescan(S sender) {
		changes.execute(() -> reply(sender, "rescanned", argument("count", extensions.get().rescan().size())));
	}

	private ComponentLike[] described(InstalledExtension extension, ComponentLike... more) {
		ExtensionDescriptor descriptor = extension.getDescriptor();
		List<ComponentLike> described = new ArrayList<>(List.of(
				argument("id", extension.getId()),
				argument("version", descriptor == null ? "" : descriptor.getVersion().toString()),
				argument("state", Component.translatable(keys + "state." + extension.getState().name().toLowerCase(Locale.ROOT))),
				argument("reason", reason(extension))
		));
		described.addAll(List.of(more));

		return described.toArray(ComponentLike[]::new);
	}

	private Component reason(InstalledExtension extension) {
		String why = switch (extension.getState()) {
			case WAITING -> String.join(", ", extension.getWaitingFor());
			case FAILED -> extension.getFailure() == null ? "" : String.valueOf(extension.getFailure().getMessage());
			default -> "";
		};
		if (why.isEmpty()) return Component.empty();

		return Component.translatable(keys + "reason", argument("why", why));
	}

	private ComponentLike argument(String name, Object value) {
		return arguments.apply(name, value);
	}

	private void reply(S sender, String message, ComponentLike... values) {
		replies.accept(sender, Component.translatable(keys + message, values));
	}

	private enum Change {
		ENABLE,
		DISABLE,
		RELOAD
	}

	/**
	 * Builds the commands.
	 *
	 * @param <S> the host's sender
	 */
	public static final class Builder<S> {
		private final CommandManager<S> manager;
		private final Supplier<Extensions> extensions;
		private final String root;
		private final String kind;
		private BiPredicate<S, String> permissions = (sender, permission) -> false;
		private BiConsumer<S, Component> replies = (sender, message) -> {
		};
		private BiFunction<String, Object, ComponentLike> arguments = (name, value) -> value instanceof ComponentLike component ? component : Component.text(String.valueOf(value));
		private Executor changes = Runnable::run;
		private String keys;

		private Builder(CommandManager<S> manager, Supplier<Extensions> extensions, String root, String kind) {
			this.manager = manager;
			this.extensions = extensions;
			this.root = root;
			this.kind = kind;
			this.keys = root + "." + kind + ".";
		}

		/**
		 * Decides whether a sender holds a permission; nobody does when it is not set.
		 *
		 * @param permissions the check
		 * @return this builder
		 */
		public @NotNull Builder<S> permissions(@NotNull BiPredicate<S, String> permissions) {
			this.permissions = permissions;

			return this;
		}

		/**
		 * Sends an answer to a sender.
		 *
		 * @param replies sends it
		 * @return this builder
		 */
		public @NotNull Builder<S> replies(@NotNull BiConsumer<S, Component> replies) {
			this.replies = replies;

			return this;
		}

		/**
		 * Gives the prefix of every answer's translation key, ending with a dot; {@code <root>.<kind>.} when it is not
		 * set, such as {@code arbor.module.}, so the list's header is {@code arbor.module.list.header}.
		 *
		 * @param keys the prefix
		 * @return this builder
		 */
		public @NotNull Builder<S> keys(@NotNull String keys) {
			this.keys = keys;

			return this;
		}

		/**
		 * Makes a named argument of an answer from its name and its value, such as Semantica's
		 * {@code TextArgument::of}. A value keeps its type: a count is an {@link Integer}, so a translation may choose a
		 * plural form by it, and a state or a reason is a translatable {@link Component}. When it is not set, each value
		 * is an argument by position, in the order the class lists them, a value that is no component as its text.
		 *
		 * @param arguments makes an argument from a name and a value
		 * @return this builder
		 */
		public @NotNull Builder<S> arguments(@NotNull BiFunction<String, Object, ComponentLike> arguments) {
			this.arguments = arguments;

			return this;
		}

		/**
		 * Gives the executor changes run on; the caller's thread when it is not set.
		 *
		 * @param changes the executor
		 * @return this builder
		 */
		public @NotNull Builder<S> changes(@NotNull Executor changes) {
			this.changes = changes;

			return this;
		}

		/**
		 * Builds the commands; {@link ExtensionCommands#register()} registers them.
		 *
		 * @return the commands
		 */
		public @NotNull ExtensionCommands<S> build() {
			return new ExtensionCommands<>(this);
		}
	}
}
