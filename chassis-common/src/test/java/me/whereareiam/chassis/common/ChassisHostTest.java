package me.whereareiam.chassis.common;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.ExtensionPreparation;
import me.whereareiam.chassis.ExtensionTransformer;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.common.loading.Marking;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
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
import org.example.peer.PeerApi;
import org.example.peerhidden.PeerInternal;
import org.example.peeruser.PeerUserModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	private final Map<String, PlatformPeer> peers = new HashMap<>();
	private ChassisHost<ExtensionContext, Void> host;

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
		System.clearProperty("chassis.test.dependent.sharedFrom");
		System.clearProperty("chassis.test.peeruser.saw");
		System.clearProperty("chassis.test.peeruser.internal");
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
	void aDependentSeesALibraryOfItsDependencyThatTheDependencyExports() throws Exception {
		Path library = ExtensionJar.of("library.txt", "").with(LibraryText.class).write(folder.resolve("libraries/library.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 1.4.0
				main: org.example.greeting.GreetingModule
				exports: [org.example.greeting, org.example.library]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/greeting.jar"));
		dependent("^1");

		start(loader -> {
			if (loader.descriptor().getId().equals("greeting")) loader.addJar(library);
			return () -> {
			};
		});

		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertEquals("true", System.getProperty("chassis.test.dependent.library"));
	}

	@Test
	void aPackageADependencyExportsIsItsEvenWhenTheDependentBundlesACopy() throws Exception {
		greeting();
		ExtensionJar.of(DESCRIPTOR, """
				id: dependent
				version: 1.0.0
				main: org.example.dependent.DependentModule
				dependencies: [{id: greeting}]
				""").with(DependentModule.class, Shared.class).write(folder.resolve("modules/dependent.jar"));

		start();

		assertEquals(ExtensionState.ENABLED, state("dependent"));
		assertEquals("chassis-extension:greeting", System.getProperty("chassis.test.dependent.sharedFrom"));
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
	void anExtensionWaitsForAPeerUntilThePlatformHasIt() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				peers: [{id: luckperms}]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/greeting.jar"));
		start();
		assertEquals(List.of("the peer 'luckperms', which is not loaded"), host.find("greeting").orElseThrow().getWaitingFor());

		peers.put("luckperms", PlatformPeer.builder().id("luckperms").version("5.4.1").build());
		host.rescan();

		assertEquals(ExtensionState.ENABLED, state("greeting"));
	}

	@Test
	void anExtensionSeesThePackagesItNamesOfAPeerFromThePeerEvenWithACopyOfItsOwn() throws Exception {
		URLClassLoader peerLoader = new URLClassLoader("test-peer",
				new URL[]{ExtensionJar.of("peer.yml", "name: Plugin").with(PeerApi.class, PeerInternal.class).write(folder.resolve("peer.jar")).toUri().toURL()},
				ClassLoader.getPlatformClassLoader());
		peers.put("peer", PlatformPeer.builder().id("peer").version("2.1.0").classLoader(peerLoader).build());
		ExtensionJar.of(DESCRIPTOR, """
				id: peer-user
				version: 1.0.0
				main: org.example.peeruser.PeerUserModule
				peers:
				  - id: peer
				    version: ^2
				    packages: [org.example.peer]
				  - id: absent
				    optional: true
				""").with(PeerUserModule.class, PeerApi.class).write(folder.resolve("modules/peer-user.jar"));

		start();

		assertEquals(ExtensionState.ENABLED, state("peer-user"));
		assertEquals(PeerApi.NAME + " from test-peer", System.getProperty("chassis.test.peeruser.saw"));
		assertEquals("false", System.getProperty("chassis.test.peeruser.internal"));
		peerLoader.close();
	}

	@Test
	void anExtensionWaitsForAPeerInAVersionItAccepts() throws Exception {
		peers.put("luckperms", PlatformPeer.builder().id("luckperms").version("5.4.1").build());
		peers.put("odd", PlatformPeer.builder().id("odd").version("build 7").build());
		ExtensionJar.of(DESCRIPTOR, """
				id: greeting
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				peers:
				  - id: luckperms
				    version: ^6
				  - id: odd
				    version: ^1
				    optional: true
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/greeting.jar"));

		start();

		assertEquals(List.of("the peer 'luckperms' ^6, and 5.4.1 is loaded", "the peer 'odd' ^1, whose version 'build 7' is no version"),
				host.find("greeting").orElseThrow().getWaitingFor());
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
				platforms: [{name: paper}]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/gateway-user.jar"));

		start();

		assertEquals("It is built for the host's API ^2, and the host's API is 1.2.0", host.find("future").orElseThrow().getFailure().getMessage());
		assertEquals("It requires 'persistence', which the host does not offer", host.find("needy").orElseThrow().getFailure().getMessage());
		assertEquals("It runs on paper, not on test", host.find("gateway-user").orElseThrow().getFailure().getMessage());
	}

	@Test
	void anExtensionRunsOnlyOnAVersionOfThePlatformItAccepts() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: older
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				platforms: [{name: test, version: ">=1.16 <1.22 || ~26.1"}]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/older.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: newer
				version: 1.0.0
				main: org.example.greeting.GreetingModule
				platforms: [{name: paper}, {name: test, version: "~26.1"}]
				""").with(GreetingModule.class, Shared.class).write(folder.resolve("modules/newer.jar"));

		start();

		assertEquals(ExtensionState.ENABLED, state("older"));
		assertEquals("It runs on paper, test ~26.1, not on test 1.21.4", host.find("newer").orElseThrow().getFailure().getMessage());
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

	@Test
	void aHostGetsItsOwnFieldsOfEachDescriptorTypedWhenItMakesTheContextAndAfter() throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: library
				version: 1.0.0
				main: org.example.library.LibraryModule
				updater: {release: example/Library}
				""").with(LibraryModule.class).write(folder.resolve("modules/library.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: odd
				version: 1.0.0
				main: org.example.library.LibraryModule
				updater: [not, a, map]
				""").with(LibraryModule.class).write(folder.resolve("modules/odd.jar"));
		List<ModuleFields> seen = new ArrayList<>();
		ChassisHost<ExtensionContext, ModuleFields> fielded = new ChassisHost<>(options(ExtensionPreparation.NONE),
				TestModule.class, ModuleFields.class, (base, fields) -> {
			seen.add(fields);
			return base;
		}, ExtensionFactory.CONSTRUCTOR);

		fielded.start();
		try {
			ModuleFields expected = new ModuleFields(new Updater("example/Library"));
			assertEquals(List.of(expected), seen);
			assertEquals(Optional.of(expected), fielded.fields("library"));
			assertEquals(ExtensionState.FAILED, fielded.find("odd").orElseThrow().getState());
			assertTrue(fielded.find("odd").orElseThrow().getFailure().getMessage()
					.startsWith("The descriptor of odd has fields of the host that cannot be read: "));
			assertEquals(Optional.empty(), fielded.fields("odd"));
		} finally {
			fielded.stop();
		}
	}

	@Test
	void anExtensionRunsFromItsTransformedJarKeptInTheDataFolderByDefault() throws Exception {
		greeting();
		Marking marking = new Marking("marking");
		host = new ChassisHost<>(options(ExtensionPreparation.NONE, List.of(marking)), TestModule.class, base -> base,
				ExtensionFactory.CONSTRUCTOR);

		host.start();
		host.reload("greeting");

		assertEquals(ExtensionState.ENABLED, state("greeting"));
		assertEquals(List.of("greeting.jar"), marking.transformed);
		assertNotNull(host.classLoader("greeting").getResource("transformed.txt"));
		assertTrue(Files.isRegularFile(folder.resolve("data/.transformed/greeting-marking-1.jar")));
		assertEquals(folder.resolve("modules/greeting.jar"), host.find("greeting").orElseThrow().getJar());
	}

	record ModuleFields(Updater updater) {
	}

	record Updater(String release) {
	}

	private void start() throws Exception {
		start(ExtensionPreparation.NONE);
	}

	private void start(ExtensionPreparation preparation) throws Exception {
		host = new ChassisHost<>(options(preparation), TestModule.class, base -> base, ExtensionFactory.CONSTRUCTOR);
		host.start();
	}

	private HostOptions options(ExtensionPreparation preparation) {
		return options(preparation, List.of());
	}

	private HostOptions options(ExtensionPreparation preparation, List<ExtensionTransformer> transformers) {
		return HostOptions.builder()
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
					public Version version() {
						return Version.parse("1.21.4");
					}

					@Override
					public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
						return Optional.ofNullable(peers.get(id));
					}
				})
				.exportLayer(new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis")))
				.preparation(preparation)
				.transformers(transformers)
				.build();
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
