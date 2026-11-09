package me.whereareiam.chassis.common;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.ExtensionPreparation;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.service.ServiceKey;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import org.example.dependent.DependentModule;
import org.example.failing.FailingModule;
import org.example.greeting.GreetingModule;
import org.example.greeting.Shared;
import org.example.secret.Secret;
import org.example.library.LibraryModule;
import org.example.library.LibraryText;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChassisHostTest {
	private static final String DESCRIPTOR = "test-module.yml";

	@TempDir
	Path folder;

	private final Set<String> plugins = new java.util.HashSet<>();
	private ChassisHost<ExtensionContext> host;

	@BeforeEach
	void clearMarks() {
		System.clearProperty("chassis.test.greeting.closed");
		System.clearProperty("chassis.test.failing.closed");
		System.clearProperty("chassis.test.dependent.saw");
		System.clearProperty("chassis.test.dependent.junit");
		System.clearProperty("chassis.test.library.saw");
		System.clearProperty("chassis.test.dependent.shared");
		System.clearProperty("chassis.test.dependent.secret");
		System.clearProperty("chassis.test.dependent.library");
	}

	@AfterEach
	void stopHost() {
		if (host != null) host.stop();
	}

	@Test
	void startsEveryExtensionAfterItsDependenciesWhichSeeTheirClassesAndServices() throws Exception {
		greeting();
		dependent("^1");

		start();

		assertEquals(List.of("greeting", "dependent"), host.list().stream().map(InstalledExtension::getId).toList());
		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertEquals(Shared.GREETING + " 1", System.getProperty("chassis.test.dependent.saw"));
	}

	@Test
	void anExtensionSeesTheExportsAndNothingElseOfTheHost() throws Exception {
		greeting();
		dependent("^1");

		start();

		assertEquals("false", System.getProperty("chassis.test.dependent.junit"));
	}

	@Test
	void anExtensionSeesOfItsDependencyOnlyWhatItExports() throws Exception {
		Path library = ExtensionJar.of("library.txt", "").with(LibraryText.class).write(folder.resolve("libraries/library.jar"));
		greeting();
		dependent("^1");

		start(loader -> {
			if (loader.descriptor().getId().equals("greeting")) loader.addJar(library);
			return () -> {
			};
		});

		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertEquals("true", System.getProperty("chassis.test.dependent.shared"));
		assertEquals("false", System.getProperty("chassis.test.dependent.secret"));
		assertEquals("false", System.getProperty("chassis.test.dependent.library"));
	}

	@Test
	void anExtensionSeesNoClassOfADependencyThatExportsNothing() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 1.4.0
				main: org.example.greeting.GreetingModule
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/greeting.jar"));
		dependent("^1");

		start();

		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertEquals("false", System.getProperty("chassis.test.dependent.shared"));
	}

	@Test
	void turningAnExtensionOffStopsItsDependentsFirstAndKeepsTheChoice() throws Exception {
		greeting();
		dependent("^1");
		start();
		List<String> changes = new ArrayList<>();
		host.subscribe(event -> changes.add(event.getExtension().getId() + " " + event.getExtension().getState()));

		host.disable("greeting");

		assertEquals(List.of("dependent WAITING", "greeting DISABLED"), changes);
		assertEquals(List.of("'greeting' ^1 (disabled)"), host.find("dependent").orElseThrow().getWaitingFor());
		assertEquals("true", System.getProperty("chassis.test.greeting.closed"));
		assertTrue(host.services().find(ServiceKey.of(Greeting.class)).isEmpty());
		assertEquals(Set.of("greeting"), new SelectionFile(folder.resolve("modules.yml"), "module").disabled());

		host.stop();
		start();
		assertEquals(ExtensionState.DISABLED, state("greeting"));

		host.enable("greeting");
		assertEquals(ExtensionState.ENABLED, state("greeting"));
		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertEquals(Set.of(), new SelectionFile(folder.resolve("modules.yml"), "module").disabled());
	}

	@Test
	void reloadingLoadsTheJarAgainInAFreshLoaderAndTheOldOneIsCollected() throws Exception {
		greeting();
		dependent("^1");
		start();
		WeakReference<ClassLoader> before = new WeakReference<>(host.classLoader("greeting"));

		host.reload("greeting");

		assertEquals(ExtensionState.ENABLED, state("greeting"));
		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertNotSame(before.get(), host.classLoader("greeting"));
		// The class started over in its new loader, so the dependent saw its first greeting again.
		assertEquals(Shared.GREETING + " 1", System.getProperty("chassis.test.dependent.saw"));
		assertTrue(LeakCheck.collected(before, Duration.ofSeconds(10)), "the old loader of greeting was not collected");
	}

	@Test
	void anExtensionThatFailsToStartLosesWhatItRegisteredAndItsDependentsWait() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: failing
				version: 1.0.0
				main: org.example.failing.FailingModule
				""").with(FailingModule.class).write(folder.resolve("modules/failing.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: dependent
				version: 1.0.0
				main: org.example.dependent.DependentModule
				dependencies:
				  - id: failing
				""").with(DependentModule.class).write(folder.resolve("modules/dependent.jar"));

		start();

		InstalledExtension failing = host.find("failing").orElseThrow();
		assertEquals(ExtensionState.FAILED, failing.getState());
		assertNotNull(failing.getFailure());
		assertEquals("true", System.getProperty("chassis.test.failing.closed"));
		assertTrue(host.services().find(ServiceKey.of(Greeting.class, "failing")).isEmpty());
		assertEquals(List.of("'failing' * (failed)"), host.find("dependent").orElseThrow().getWaitingFor());
	}

	@Test
	void theFailureKeptOfAnExtensionThatFailedToStartKeepsNoneOfItsClasses() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: failing
				version: 1.0.0
				main: org.example.failing.FailingModule
				""").with(FailingModule.class).write(folder.resolve("modules/failing.jar"));

		start();

		assertEquals(ExtensionState.FAILED, state("failing"));
		assertTrue(host.find("failing").orElseThrow().getFailure().getCause().getMessage()
				.equals(IllegalStateException.class.getName() + ": The failing module does not start"));
		assertTrue(LeakCheck.collected(Witness.failingLoader, Duration.ofSeconds(10)), "the kept failure holds the failed extension's loader");
	}

	@Test
	void anExtensionUsesTheJarsItsPreparationAddedWhichCloseWhenItStops() throws Exception {
		Path library = ExtensionJar.of("library.txt", "").with(LibraryText.class).write(folder.resolve("libraries/library.jar"));
		library();
		List<String> prepared = new ArrayList<>();
		start(loader -> {
			prepared.add(loader.descriptor().getId() + " " + loader.jar().getFileName());
			loader.addJar(library);
			return () -> prepared.add("closed");
		});

		assertEquals(ExtensionState.ENABLED, state("library"));
		assertEquals(LibraryText.TEXT, System.getProperty("chassis.test.library.saw"));

		host.stop();

		assertEquals(List.of("library library.jar", "closed"), prepared);
	}

	@Test
	void anExtensionWhosePreparationFailsDoesNotStart() throws Exception {
		library();
		start(loader -> {
			throw new IllegalStateException("No libraries today");
		});

		InstalledExtension library = host.find("library").orElseThrow();
		assertEquals(ExtensionState.FAILED, library.getState());
		assertTrue(library.getFailure().getMessage().contains("No libraries today"));
		assertNull(System.getProperty("chassis.test.library.saw"));
	}

	@Test
	void anExtensionThatFailsToStartClosesWhatItsPreparationReturned() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: failing
				version: 1.0.0
				main: org.example.failing.FailingModule
				""").with(FailingModule.class).write(folder.resolve("modules/failing.jar"));
		List<String> closed = new ArrayList<>();
		start(loader -> () -> closed.add(loader.descriptor().getId()));

		assertEquals(ExtensionState.FAILED, state("failing"));
		assertEquals(List.of("failing"), closed);
	}

	@Test
	void anExtensionWaitsForAMissingDependencyAndStartsWhenARescanFindsIt() throws Exception {
		dependent("^1");
		start();
		assertEquals(List.of("'greeting' ^1 (not installed)"), host.find("dependent").orElseThrow().getWaitingFor());

		greeting();
		host.rescan();

		assertEquals(ExtensionState.ENABLED, state("greeting"));
		assertEquals(ExtensionState.ENABLED, state("dependent"));
	}

	@Test
	void anExtensionWaitsForAPluginUntilThePlatformHasIt() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				plugins: [luckperms]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/greeting.jar"));
		start();
		assertEquals(List.of("the plugin 'luckperms', which is not loaded"), host.find("greeting").orElseThrow().getWaitingFor());

		plugins.add("luckperms");
		host.rescan();

		assertEquals(ExtensionState.ENABLED, state("greeting"));
	}

	@Test
	void anExtensionThatDoesNotFitTheHostFails() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: future
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				host: ^2
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/future.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: needy
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				requires:
				  persistence: ^1
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/needy.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: gateway-user
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				requires:
				  gateway: ^1
				platforms: [paper]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/gateway-user.jar"));

		start();

		assertEquals("It is built for the host's API ^2, and the host's API is 1.2.0", host.find("future").orElseThrow().getFailure().getMessage());
		assertEquals("It requires 'persistence', which the host does not offer", host.find("needy").orElseThrow().getFailure().getMessage());
		assertEquals("It runs on paper, not on test", host.find("gateway-user").orElseThrow().getFailure().getMessage());
	}

	@Test
	void extensionsWhoseDependenciesFormACycleFail() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: left
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				dependencies: [{id: right}]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/left.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: right
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				dependencies: [{id: left}]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/right.jar"));

		start();

		assertEquals(ExtensionState.FAILED, state("left"));
		assertEquals(ExtensionState.FAILED, state("right"));
		assertTrue(host.find("left").orElseThrow().getFailure().getMessage().startsWith("Its dependencies form a cycle"));
	}

	@Test
	void aJarWithoutADescriptorOrWithAnotherExtensionsIdFails() throws Exception {
		greeting();
		ExtensionJar.of("other.yml", "id: x").write(folder.resolve("modules/broken.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 2.0.0
				main: org.example.greeting.GreetingModule
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/second-greeting.jar"));

		start();

		assertEquals("broken.jar has no " + DESCRIPTOR, host.find("broken").orElseThrow().getFailure().getMessage());
		assertEquals(ExtensionState.ENABLED, state("greeting"));
		assertEquals(ExtensionState.FAILED, state("second-greeting"));
	}

	@Test
	void stoppingTheHostStopsEveryExtensionDependentsFirst() throws Exception {
		greeting();
		dependent("^1");
		start();
		List<String> changes = new ArrayList<>();
		host.subscribe(event -> changes.add(event.getExtension().getId()));

		host.stop();

		assertEquals(List.of("dependent", "greeting"), changes);
		assertTrue(host.list().stream().allMatch(extension -> extension.getState() == ExtensionState.WAITING));
		assertFalse(Files.exists(folder.resolve("modules.yml")));
	}

	private void start() throws Exception {
		start(ExtensionPreparation.NONE);
	}

	private void start(ExtensionPreparation preparation) throws Exception {
		HostOptions options = HostOptions.builder()
				.kind("module")
				.folder(folder.resolve("modules"))
				.descriptorFile(DESCRIPTOR)
				.dataFolder(folder.resolve("data"))
				.selectionFile(folder.resolve("modules.yml"))
				.hostVersion(Version.parse("1.2.0"))
				.offer("gateway", Version.parse("1.0.0"))
				.platform(new Platform() {
					@Override
					public @NotNull String name() {
						return "test";
					}

					@Override
					public boolean hasPlugin(@NotNull String id) {
						return plugins.contains(id);
					}
				})
				.exportLayer(new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis")))
				.preparation(preparation)
				.build();
		host = new ChassisHost<>(options, TestModule.class, base -> base, ExtensionFactory.CONSTRUCTOR);
		host.start();
	}

	private ExtensionState state(String id) {
		return host.find(id).orElseThrow().getState();
	}

	private void greeting() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				name: Greeting
				version: 1.4.0
				main: org.example.greeting.GreetingModule
				exports: [org.example.greeting]
				""").with(GreetingModule.class, Shared.class, Secret.class).write(folder.resolve("modules/greeting.jar"));
	}

	private void library() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: library
				version: 1.0.0
				main: org.example.library.LibraryModule
				""").with(LibraryModule.class).write(folder.resolve("modules/library.jar"));
	}

	private void dependent(String range) throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: dependent
				version: 1.0.0
				main: org.example.dependent.DependentModule
				dependencies:
				  - id: greeting
				    version: "%s"
				""".formatted(range)).with(DependentModule.class).write(folder.resolve("modules/dependent.jar"));
	}
}
