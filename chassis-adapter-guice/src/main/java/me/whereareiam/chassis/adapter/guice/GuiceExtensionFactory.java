package me.whereareiam.chassis.adapter.guice;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.service.Services;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Makes each extension's main class through an injector of its own, which offers the extension its context, its
 * services and descriptor, and the keys the host exports from its own injector, each handed over by the host's
 * injector when asked for. The extension's injector is never a child of the host's: Guice puts a child's
 * just-in-time bindings into its parent whenever the parent can satisfy them, so the host would keep the extension's
 * classes, and their class loader, after the extension stopped.
 *
 * <pre>{@code
 * ExtensionFactory factory = GuiceExtensionFactory.builder(hostInjector, ModuleContext.class)
 *         .export(Key.get(Scheduler.class))
 *         .export(Key.get(Gateway.class))
 *         .build();
 * }</pre>
 *
 * <p>An exported key's type must be one the extension sees through the host's export layer. Guice itself may be
 * relocated in the host; extensions write {@code jakarta.inject} annotations, which the host exports.</p>
 *
 * @param <C> the host's context
 */
public final class GuiceExtensionFactory<C extends ExtensionContext> implements ExtensionFactory {
	private final @NotNull Injector host;
	private final @NotNull Class<C> contextType;
	private final @NotNull List<Key<?>> exported;
	private final @NotNull BiConsumer<Binder, C> bindings;

	private GuiceExtensionFactory(Builder<C> builder) {
		this.host = builder.host;
		this.contextType = builder.contextType;
		this.exported = List.copyOf(builder.exported);
		this.bindings = builder.bindings;
	}

	/**
	 * Starts the factory.
	 *
	 * @param host        the host's injector, which answers the exported keys
	 * @param contextType the host's context type, which the extension's injector binds to the context of its load
	 * @param <C>         the host's context
	 * @return the builder
	 */
	public static <C extends ExtensionContext> @NotNull Builder<C> builder(@NotNull Injector host, @NotNull Class<C> contextType) {
		return new Builder<>(host, contextType);
	}

	@Override
	public <T> @NotNull T create(@NotNull Class<T> main, @NotNull ExtensionContext context) {
		C typed = contextType.cast(context);
		Injector injector = Guice.createInjector(Stage.PRODUCTION, binder -> {
			binder.bind(contextType).toInstance(typed);
			if (contextType != ExtensionContext.class) binder.bind(ExtensionContext.class).toInstance(typed);
			binder.bind(Services.class).toInstance(typed.services());
			binder.bind(ExtensionDescriptor.class).toInstance(typed.descriptor());
			for (Key<?> key : exported) export(binder, key);
			bindings.accept(binder, typed);
		});

		return injector.getInstance(main);
	}

	private <T> void export(Binder binder, Key<T> key) {
		binder.bind(key).toProvider(() -> host.getInstance(key));
	}

	/**
	 * Builds the factory.
	 *
	 * @param <C> the host's context
	 */
	public static final class Builder<C extends ExtensionContext> {
		private final Injector host;
		private final Class<C> contextType;
		private final java.util.ArrayList<Key<?>> exported = new java.util.ArrayList<>();
		private BiConsumer<Binder, C> bindings = (binder, context) -> {
		};

		private Builder(Injector host, Class<C> contextType) {
			this.host = host;
			this.contextType = contextType;
		}

		/**
		 * Offers extensions a key of the host's injector.
		 *
		 * @param key the key
		 * @return this builder
		 */
		public @NotNull Builder<C> export(@NotNull Key<?> key) {
			exported.add(key);

			return this;
		}

		/**
		 * Offers extensions the host's binding of a type.
		 *
		 * @param type the type
		 * @return this builder
		 */
		public @NotNull Builder<C> export(@NotNull Class<?> type) {
			return export(Key.get(type));
		}

		/**
		 * Adds bindings of the host's own to every extension's injector, made from the context of the load, such as
		 * an extension's configuration.
		 *
		 * @param bindings adds the bindings
		 * @return this builder
		 */
		public @NotNull Builder<C> bindings(@NotNull BiConsumer<Binder, C> bindings) {
			this.bindings = bindings;

			return this;
		}

		/**
		 * Builds the factory.
		 *
		 * @return the factory
		 */
		public @NotNull GuiceExtensionFactory<C> build() {
			return new GuiceExtensionFactory<>(this);
		}
	}
}
