package me.whereareiam.chassis.adapter.spring;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import org.example.spring.GreetingModule;
import org.example.spring.HelloWords;
import org.example.spring.Hidden;
import org.example.spring.api.Words;
import org.example.spring.user.PlainModule;
import org.example.spring.user.UserModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringExtensionFactoryTest {
	private static final String DESCRIPTOR = "spring-module.yml";

	@TempDir
	Path folder;

	private AnnotationConfigApplicationContext hostContext;
	private ChassisHost<ExtensionContext> host;

	@BeforeEach
	void startHostContext() {
		System.clearProperty("chassis.test.spring.greeting");
		System.clearProperty("chassis.test.spring.user");
		hostContext = new AnnotationConfigApplicationContext();
		hostContext.registerBean(Clock.class, () -> () -> 42);
		hostContext.refresh();
	}

	@AfterEach
	void stopHost() {
		if (host != null) host.stop();
		hostContext.close();
	}

	@Test
	void anExtensionIsMadeFromItsConfigurationWithItsContextAndTheHostsBeans() throws Exception {
		greeting();

		start();

		assertEquals(ExtensionState.ENABLED, state("greeting"));
		assertEquals("hello from greeting at 42", System.getProperty("chassis.test.spring.greeting"));
	}

	@Test
	void aDependentGetsOnlyTheBeansOfExportedTypesAndBothAreCollectedOnceStopped() throws Exception {
		greeting();
		ExtensionJar.of(DESCRIPTOR, """
				id: user
				version: 1.0.0
				main: org.example.spring.user.UserModule
				dependencies: [{id: greeting}]
				""").with(UserModule.class).write(folder.resolve("modules/user.jar"));

		start();

		assertEquals(ExtensionState.ENABLED, state("user"));
		assertEquals("hello from greeting at 42 false", System.getProperty("chassis.test.spring.user"));

		WeakReference<ClassLoader> greeting = new WeakReference<>(host.classLoader("greeting"));
		WeakReference<ClassLoader> user = new WeakReference<>(host.classLoader("user"));
		host.disable("greeting");

		assertTrue(LeakCheck.collected(user, Duration.ofSeconds(10)), "the dependent's loader was kept");
		assertTrue(LeakCheck.collected(greeting, Duration.ofSeconds(10)), "the dependency's loader was kept");
	}

	@Test
	void aMainClassThatIsNoConfigurationFails() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: plain
				version: 1.0.0
				main: org.example.spring.user.PlainModule
				""").with(PlainModule.class).write(folder.resolve("modules/plain.jar"));

		start();

		InstalledExtension plain = host.find("plain").orElseThrow();
		assertEquals(ExtensionState.FAILED, plain.getState());
		assertTrue(plain.getFailure().getMessage().contains("The main class org.example.spring.user.PlainModule of 'plain' is no Spring @Configuration"),
				plain.getFailure().getMessage());
	}

	private void greeting() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 1.0.0
				main: org.example.spring.GreetingModule
				exports: [org.example.spring.api]
				""").with(GreetingModule.class, HelloWords.class, Hidden.class, Words.class).write(folder.resolve("modules/greeting.jar"));
	}

	private void start() throws Exception {
		HostOptions options = HostOptions.builder()
				.kind("module")
				.folder(folder.resolve("modules"))
				.descriptorFile(DESCRIPTOR)
				.dataFolder(folder.resolve("data"))
				.selectionFile(folder.resolve("modules.yml"))
				.hostVersion(Version.parse("1.0.0"))
				.platform(new Platform() {
					@Override
					public @NotNull String name() {
						return "test";
					}

					@Override
					public boolean hasPlugin(@NotNull String id) {
						return false;
					}
				})
				.exportLayer(new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis", "org.springframework")))
				.build();
		host = new ChassisHost<>(options, SpringModule.class, base -> base, new SpringExtensionFactory(hostContext));
		host.start();
	}

	private ExtensionState state(String id) {
		return host.find(id).orElseThrow().getState();
	}
}
