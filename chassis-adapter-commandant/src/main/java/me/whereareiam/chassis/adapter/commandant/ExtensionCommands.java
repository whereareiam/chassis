package me.whereareiam.chassis.adapter.commandant;

import me.whereareiam.chassis.Extensions;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.type.ExtensionState;
import me.whereareiam.commandant.CommandantKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.permission.PredicatePermission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
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
 *         .messages(messages.getModules())
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
 * @param <S> the host's sender
 */
public final class ExtensionCommands<S> {
	private static final MiniMessage MINI = MiniMessage.miniMessage();

	private final @NotNull CommandManager<S> manager;
	private final @NotNull Supplier<Extensions> extensions;
	private final @NotNull String root;
	private final @NotNull String kind;
	private final @NotNull BiPredicate<S, String> permissions;
	private final @NotNull BiConsumer<S, Component> replies;
	private final @NotNull Supplier<ExtensionMessages> messages;
	private final @NotNull Executor changes;

	private ExtensionCommands(Builder<S> builder) {
		this.manager = builder.manager;
		this.extensions = builder.extensions;
		this.root = builder.root;
		this.kind = builder.kind;
		this.permissions = builder.permissions;
		this.replies = builder.replies;
		this.messages = builder.messages;
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
		manager.command(command("list", "Lists every installed " + kind + " and its state").handler(context -> list(context.sender())));
		manager.command(command("info", "Shows a " + kind + ", and why it waits or failed")
				.required("id", stringParser(), ids())
				.handler(context -> info(context.sender(), context.get("id"))));
		manager.command(command("enable", "Turns a " + kind + " on and starts it")
				.required("id", stringParser(), ids())
				.handler(context -> change(context.sender(), context.get("id"), Change.ENABLE)));
		manager.command(command("disable", "Turns a " + kind + " off; those that depend on it wait")
				.required("id", stringParser(), ids())
				.handler(context -> change(context.sender(), context.get("id"), Change.DISABLE)));
		manager.command(command("reload", "Loads a " + kind + " again from its jar")
				.required("id", stringParser(), ids())
				.handler(context -> change(context.sender(), context.get("id"), Change.RELOAD)));
		manager.command(command("rescan", "Installs the " + kind + "s added since and starts what can start")
				.handler(context -> rescan(context.sender())));
	}

	private Command.Builder<S> command(String name, String description) {
		String permission = root + ".command." + kind + "." + name;

		return manager.commandBuilder(root)
				.literal(kind)
				.literal(name)
				.commandDescription(Description.of(description))
				.meta(CommandantKeys.DEFINITION_ID, kind + "-" + name)
				.permission(PredicatePermission.<S>of(sender -> permissions.test(sender, permission)));
	}

	private SuggestionProvider<S> ids() {
		return SuggestionProvider.blockingStrings((context, input) -> extensions.get().list().stream().map(InstalledExtension::getId).toList());
	}

	private void list(S sender) {
		List<InstalledExtension> installed = extensions.get().list();
		ExtensionMessages words = messages.get();
		if (installed.isEmpty()) {
			reply(sender, words.getListEmpty());
			return;
		}

		reply(sender, words.getListHeader(), Placeholder.unparsed("count", Integer.toString(installed.size())));
		for (InstalledExtension extension : installed) reply(sender, words.getListLine(), of(extension));
	}

	private void info(S sender, String id) {
		ExtensionMessages words = messages.get();
		extensions.get().find(id).ifPresentOrElse(extension -> {
			ExtensionDescriptor descriptor = extension.getDescriptor();
			reply(sender, words.getInfo(), of(extension),
					Placeholder.unparsed("name", descriptor == null ? extension.getId() : descriptor.getName()),
					Placeholder.unparsed("description", descriptor == null || descriptor.getDescription() == null ? "" : descriptor.getDescription()),
					Placeholder.unparsed("authors", descriptor == null || descriptor.getAuthors().isEmpty() ? "unknown" : String.join(", ", descriptor.getAuthors())),
					Placeholder.unparsed("jar", extension.getJar().getFileName().toString()));
		}, () -> reply(sender, words.getUnknown(), Placeholder.unparsed("id", id)));
	}

	private void change(S sender, String id, Change change) {
		changes.execute(() -> {
			ExtensionMessages words = messages.get();
			try {
				InstalledExtension extension = switch (change) {
					case ENABLE -> extensions.get().enable(id);
					case DISABLE -> extensions.get().disable(id);
					case RELOAD -> extensions.get().reload(id);
				};
				String template = switch (change) {
					case ENABLE -> words.getEnabled();
					case DISABLE -> words.getDisabled();
					case RELOAD -> words.getReloaded();
				};
				reply(sender, template, of(extension));
			} catch (ExtensionException e) {
				if (extensions.get().find(id).isEmpty()) reply(sender, words.getUnknown(), Placeholder.unparsed("id", id));
				else reply(sender, words.getRefused(), Placeholder.unparsed("reason", e.getMessage()));
			}
		});
	}

	private void rescan(S sender) {
		changes.execute(() -> reply(sender, messages.get().getRescanned(),
				Placeholder.unparsed("count", Integer.toString(extensions.get().rescan().size()))));
	}

	private TagResolver of(InstalledExtension extension) {
		ExtensionMessages words = messages.get();
		String why = switch (extension.getState()) {
			case WAITING -> String.join(", ", extension.getWaitingFor());
			case FAILED -> extension.getFailure() == null ? "" : extension.getFailure().getMessage();
			default -> "";
		};
		String state = switch (extension.getState()) {
			case ENABLED -> words.getStateEnabled();
			case DISABLED -> words.getStateDisabled();
			case WAITING -> words.getStateWaiting();
			case FAILED -> words.getStateFailed();
		};

		return TagResolver.resolver(
				Placeholder.unparsed("id", extension.getId()),
				Placeholder.unparsed("version", extension.getDescriptor() == null ? "" : extension.getDescriptor().getVersion().toString()),
				Placeholder.component("state", MINI.deserialize(state)),
				Placeholder.component("reason", why.isEmpty() || extension.getState() == ExtensionState.ENABLED ? Component.empty()
						: MINI.deserialize(words.getReason(), Placeholder.unparsed("why", why)))
		);
	}

	private void reply(S sender, String template, TagResolver... resolvers) {
		TagResolver all = TagResolver.resolver(TagResolver.resolver(resolvers), Placeholder.unparsed("kind", kind));
		replies.accept(sender, MINI.deserialize(template, all));
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
		private Supplier<ExtensionMessages> messages = ExtensionMessages::new;
		private Executor changes = Runnable::run;

		private Builder(CommandManager<S> manager, Supplier<Extensions> extensions, String root, String kind) {
			this.manager = manager;
			this.extensions = extensions;
			this.root = root;
			this.kind = kind;
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
		 * Gives the messages, asked at every answer, so that a reloaded messages file takes effect at once.
		 *
		 * @param messages the messages
		 * @return this builder
		 */
		public @NotNull Builder<S> messages(@NotNull Supplier<ExtensionMessages> messages) {
			this.messages = messages;

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
