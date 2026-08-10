import java.util.Collections
import java.util.zip.ZipFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

plugins {
    java
    kotlin("jvm") version "2.4.10"
}

group = "io.github.gyai"
version = "0.1.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

/** Only the Kotlin standard library is merged into the plugin; never use runtimeClasspath here. */
val embeddedKotlinRuntime by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    add(embeddedKotlinRuntime.name, "org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
}

configurations.named("testCompileOnly") {
    extendsFrom(configurations.compileOnly.get())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    jvmTargetValidationMode.set(org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.ERROR)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from({ embeddedKotlinRuntime.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

val inspectKotlinAuthoringJar by tasks.registering {
    dependsOn(tasks.named("jar"))
    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val resolved = embeddedKotlinRuntime.resolvedConfiguration.resolvedArtifacts
        check(resolved.size == 1) { "embeddedKotlinRuntime must resolve exactly one artifact: $resolved" }
        val stdlib = resolved.single()
        check(stdlib.moduleVersion.id.group == "org.jetbrains.kotlin"
                && stdlib.name == "kotlin-stdlib"
                && stdlib.moduleVersion.id.version == "2.4.10"
                && stdlib.file.name == "kotlin-stdlib-2.4.10.jar") {
            "embeddedKotlinRuntime must resolve only org.jetbrains.kotlin:kotlin-stdlib:2.4.10, got ${stdlib.moduleVersion.id} / ${stdlib.file.name}"
        }
        ZipFile(jarFile).use { zip ->
            val entries: List<String> = Collections.list(zip.entries()).map { it.name }
            check("kotlin/jvm/internal/Intrinsics.class" in entries) {
                "Embedded Kotlin stdlib is missing Intrinsics.class"
            }
            check("kotlin/reflect/KClass.class" in entries) {
                "Embedded Kotlin stdlib is incomplete: core kotlin.reflect.KClass is missing"
            }
            val prohibited = listOf("org/bukkit/", "io/papermc/", "kotlin/reflect/full/", "kotlin/reflect/jvm/internal/",
                "org/jetbrains/kotlin/compiler/", "org/jetbrains/kotlin/gradle/")
            check(entries.none { entry -> prohibited.any(entry::startsWith) }) {
                "Plugin jar contains prohibited dependency classes"
            }
            val duplicateProjectClasses = entries.filter { it.startsWith("io/github/gyai/projects/") }
                .groupingBy { it }.eachCount().filterValues { it > 1 }
            check(duplicateProjectClasses.isEmpty()) {
                "Plugin jar contains duplicate ProjectS entries: ${duplicateProjectClasses.keys}"
            }
        }
    }
}

/** Runs jdeps only on authoring classes, avoiding expected Paper compileOnly references from the full plugin. */
val verifyKotlinAuthoringJdeps by tasks.registering {
    dependsOn(tasks.named("jar"))
    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val extracted = layout.buildDirectory.dir("tmp/kotlin-authoring-jdeps").get().asFile
        project.delete(extracted)
        extracted.mkdirs()
        ZipFile(jarFile).use { zip ->
            Collections.list(zip.entries()).filter { it.name.startsWith("io/github/gyai/projects/authoring/") && it.name.endsWith(".class") }
                .forEach { entry ->
                    val target = extracted.toPath().resolve(entry.name.removePrefix("io/github/gyai/projects/authoring/"))
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input -> Files.copy(input, target) }
                }
        }
        val jdepsName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "jdeps.exe" else "jdeps"
        val jdeps = File(System.getProperty("java.home"), "bin/$jdepsName")
        check(jdeps.isFile) { "JDK jdeps executable not found: $jdeps" }
        val output = ByteArrayOutputStream()
        val result = ProcessBuilder(jdeps.absolutePath, "--multi-release", "25", "--missing-deps", "--class-path", jarFile.absolutePath, extracted.absolutePath)
            .redirectErrorStream(true).start().also { process -> process.inputStream.copyTo(output) }.waitFor()
        val report = output.toString(Charsets.UTF_8)
        check(result == 0) { "jdeps failed ($result): $report" }
        check(!report.contains("kotlin.")) { "Standalone authoring classes have unresolved Kotlin dependencies: $report" }
    }
}

tasks.named("build") {
    doLast {
        if (!project.hasProperty("skipAutoStart")) {
            val script = layout.projectDirectory
                .file("scripts/deploy-and-start-server.ps1").asFile.absolutePath
            val result = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", script
            ).inheritIO().start().waitFor()
            if (result != 0) {
                throw GradleException("Plugin deployment or server launch failed.")
            }
        }
    }
}

val balanceUnitTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.manager.BalanceMathTest")
    jvmArgs("-ea")
}

val ccFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.combat.skill.CcFoundationTest")
    jvmArgs("-ea")
}

val telegraphFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.telegraph.TelegraphFoundationTest")
    jvmArgs("-ea")
}

val combatShapeFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.combat.shape.CombatShapeFoundationTest")
    jvmArgs("-ea")
}

val abilityRuntimeFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.AbilityRuntimeFoundationTest")
    jvmArgs("-ea")
}
val abilityVisualFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.AbilityVisualFoundationTest")
    jvmArgs("-ea")
}
val skillEditorProtocolFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.editor.SkillVfxEditorFoundationTest")
    jvmArgs("-ea")
}
val visualSessionOverrideFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.editor.VisualSessionOverrideFoundationTest")
    jvmArgs("-ea")
}
val skillVfxEditorAuthoringV02ServerTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.editor.SkillVfxEditorAuthoringV02ServerTest")
    jvmArgs("-ea")
}
val skillVfxMotionFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.editor.SkillVfxMotionFoundationTest")
    jvmArgs("-ea")
}
tasks.named("check") {
    dependsOn(skillVfxEditorAuthoringV02ServerTest, skillVfxMotionFoundationTest)
}
val javaKotlinAuthoringInteropTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.authoring.JavaKotlinAuthoringInteropTest")
    jvmArgs("-ea")
}
val isolatedJavaKotlinAuthoringInteropTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("compileTestJava"), tasks.named("jar"), verifyKotlinAuthoringJdeps)
    classpath = files(tasks.named<JavaCompile>("compileTestJava").flatMap { it.destinationDirectory },
        tasks.named<Jar>("jar").flatMap { it.archiveFile })
    mainClass.set("io.github.gyai.projects.authoring.JavaKotlinAuthoringInteropTest")
    jvmArgs("-ea")
}
val kotlinAuthoringFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses, inspectKotlinAuthoringJar, javaKotlinAuthoringInteropTest,
        isolatedJavaKotlinAuthoringInteropTest, verifyKotlinAuthoringJdeps)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.authoring.KotlinAuthoringFoundationTest")
    jvmArgs("-ea")
}

val assignedMobAbilityFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.ability.AssignedMobAbilityFoundationTest")
    jvmArgs("-ea")
}

val hardControlTestToolListenerTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.listener.HardControlTestToolListenerTest")
    jvmArgs("-ea")
}

val mobAbilityDamageParityTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.combat.damage.MobAbilityDamageParityTest")
    jvmArgs("-ea")
}

val statFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.combat.stat.StatFoundationTest")
    jvmArgs("-ea")
}

val combatDamageFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.CombatDamageFoundationTest")
    jvmArgs("-ea")
}

val damageCalculatorCharacterizationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.DamageCalculatorCharacterizationTest")
    jvmArgs("-ea")
}

val damageSnapshotTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.combat.damage.DamageSnapshotTest")
    jvmArgs("-ea")
}

val attackMetadataAdapterTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.AttackMetadataAdapterTest")
    jvmArgs("-ea")
}

val starterSwordShadowParityTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.StarterSwordShadowParityTest")
    jvmArgs("-ea")
}

val damageShadowValidationTrackerTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.DamageShadowValidationTrackerTest")
    jvmArgs("-ea")
}

val damageShadowRuntimeSafetyTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.DamageShadowRuntimeSafetyTest")
    jvmArgs("-ea")
}

val damageShadowCommandTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.DamageShadowCommandTest")
    jvmArgs("-ea")
}

val damageShadowExportTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.DamageShadowExportTest")
    jvmArgs("-ea")
}

val starterSwordRoutePolicyTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.StarterSwordRoutePolicyTest")
    jvmArgs("-ea")
}

val starterSwordLimitedCutoverTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.StarterSwordLimitedCutoverTest")
    jvmArgs("-ea")
}

val starterSwordFallbackSafetyTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.StarterSwordFallbackSafetyTest")
    jvmArgs("-ea")
}

val starterSwordAuthoritativeShadowTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.StarterSwordAuthoritativeShadowTest")
    jvmArgs("-ea")
}

val starterSwordRouteCommandServiceTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.StarterSwordRouteCommandServiceTest")
    jvmArgs("-ea")
}

val spinSlashAttackMetadataTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.skill.warrior.SpinSlashAttackMetadataTest")
    jvmArgs("-ea")
}

val genericDamageShadowComparatorTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.GenericDamageShadowComparatorTest")
    jvmArgs("-ea")
}

val spinSlashDamageShadowRuntimeTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.SpinSlashDamageShadowRuntimeTest")
    jvmArgs("-ea")
}

val damageShadowCommandRoutingTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.damage.DamageShadowCommandRoutingTest")
    jvmArgs("-ea")
}

val mobEditorFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.monster.editor.MobEditorFoundationTest")
    jvmArgs("-ea")
}

val mobEditorV2ProtocolTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.network.MobEditorV2ProtocolTest")
    jvmArgs("-ea")
}

val shutdownSequenceTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.lifecycle.ShutdownSequenceTest")
    jvmArgs("-ea")
}

val featureFlagServiceTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.feature.FeatureFlagServiceTest")
    jvmArgs("-ea")
}

val schemaVersionRegistryTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.schema.SchemaVersionRegistryTest")
    jvmArgs("-ea")
}

val betaContractPresenceTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.beta.BetaContractPresenceTest")
    jvmArgs("-ea")
}

val trackDTransactionFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.transaction.TrackDTransactionFoundationTest")
    jvmArgs("-ea")
}

val fireElementEngineTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.element.fire.FireElementEngineTest")
    jvmArgs("-ea")
}

val iceElementEngineTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.element.ice.IceElementEngineTest")
    jvmArgs("-ea")
}

val lightningElementEngineTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "io.github.gyai.projects.combat.element.lightning.LightningElementEngineTest")
    jvmArgs("-ea")
}

val equipmentAndModFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.equipment.EquipmentAndModFoundationTest")
    jvmArgs("-ea")
}

val legacyItemCompatibilityTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.item.compatibility.LegacyItemCompatibilityTest")
    jvmArgs("-ea")
}

val playerProgressDomainTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.player.progress.PlayerProgressDomainTest")
    jvmArgs("-ea")
}

val playerProgressRepositoryTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.persistence.player.PlayerProgressRepositoryTest")
    jvmArgs("-ea")
}

val playerPersistenceCoordinatorTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.persistence.player.PlayerPersistenceCoordinatorTest")
    jvmArgs("-ea")
}

val wave1IntegratedFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.beta.Wave1IntegratedFoundationTest")
    jvmArgs("-ea")
}

val wave2OwnerDecisionContractTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gyai.projects.beta.Wave2OwnerDecisionContractTest")
    jvmArgs("-ea")
}

val trackFPartyQuestRewardFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.TrackFPartyQuestRewardFoundationTest")
    jvmArgs("-ea")
}

val legacyEnhancementCharacterizationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.enhancement.v2.LegacyEnhancementCharacterizationTest")
    jvmArgs("-ea")
}

val trackEFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.enhancement.v2.TrackEFoundationTest")
    jvmArgs("-ea")
}

val enhancementTransactionSafetyTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.equipment.operation.EnhancementTransactionSafetyTest")
    jvmArgs("-ea")
}

val wave2IntegratedFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.beta.Wave2IntegratedFoundationTest")
    jvmArgs("-ea")
}

val betaProtocolFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.network.beta.BetaProtocolFoundationTest")
    jvmArgs("-ea")
}

val mobV2FoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.monster.definition.v2.MobV2FoundationTest")
    jvmArgs("-ea")
}

val wave3IntegratedFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.beta.Wave3IntegratedFoundationTest")
    jvmArgs("-ea")
}

val betaActivationFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.BetaActivationFoundationTest")
    jvmArgs("-ea")
}

val activationTrack4RuntimeAdapterTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track4.Track4RuntimeAdapterTest")
    jvmArgs("-ea")
}

val track3StagingItemWriterTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track3.Track3StagingItemWriterTest")
    jvmArgs("-ea")
}

val track3EconomyOperationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track3.Track3EconomyOperationTest")
    jvmArgs("-ea")
}

val track3RuntimeModuleTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track3.Track3RuntimeModuleTest")
    jvmArgs("-ea")
}

val combatElementsActivationRuntimeTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track2.CombatElementsActivationRuntimeTest")
    jvmArgs("-ea")
}

val track1ActivationFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track1.Track1ActivationFoundationTest")
    jvmArgs("-ea")
}

val betaActivationWave1IntegratedTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.BetaActivationWave1IntegratedTest")
    jvmArgs("-ea")
}

val betaActivationWave1ProtocolIntegrationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.BetaActivationWave1ProtocolIntegrationTest")
    jvmArgs("-ea")
}

val betaCapabilityHandshakePreflightTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.BetaCapabilityHandshakePreflightTest")
    jvmArgs("-ea")
}

val track2CompatibilityBoundaryTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track2.Track2CompatibilityBoundaryTest")
    jvmArgs("-ea")
}

val track2ConfirmedHitPublisherIntegrationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.track2.Track2ConfirmedHitPublisherIntegrationTest")
    jvmArgs("-ea")
}

val elementSnapshotProtocolDiagnosticsTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set(
        "io.github.gyai.projects.beta.activation.ElementSnapshotProtocolDiagnosticsTest")
    jvmArgs("-ea")
}

tasks.test {
    failOnNoDiscoveredTests = false
}

tasks.named("check") {
    dependsOn(skillEditorProtocolFoundationTest)
    dependsOn(visualSessionOverrideFoundationTest)
    dependsOn(kotlinAuthoringFoundationTest)
    dependsOn(inspectKotlinAuthoringJar)
    dependsOn(combatShapeFoundationTest)
    dependsOn(abilityRuntimeFoundationTest)
    dependsOn(abilityVisualFoundationTest)
    dependsOn(assignedMobAbilityFoundationTest)
    dependsOn(hardControlTestToolListenerTest)
    dependsOn(mobAbilityDamageParityTest)
    dependsOn(elementSnapshotProtocolDiagnosticsTest)
    dependsOn(track3StagingItemWriterTest)
    dependsOn(track3EconomyOperationTest)
    dependsOn(track3RuntimeModuleTest)
    dependsOn(balanceUnitTest)
    dependsOn(ccFoundationTest)
    dependsOn(telegraphFoundationTest)
    dependsOn(statFoundationTest)
    dependsOn(combatDamageFoundationTest)
    dependsOn(damageCalculatorCharacterizationTest)
    dependsOn(damageSnapshotTest)
    dependsOn(attackMetadataAdapterTest)
    dependsOn(starterSwordShadowParityTest)
    dependsOn(damageShadowValidationTrackerTest)
    dependsOn(damageShadowRuntimeSafetyTest)
    dependsOn(damageShadowCommandTest)
    dependsOn(damageShadowExportTest)
    dependsOn(starterSwordRoutePolicyTest)
    dependsOn(starterSwordLimitedCutoverTest)
    dependsOn(starterSwordFallbackSafetyTest)
    dependsOn(starterSwordAuthoritativeShadowTest)
    dependsOn(starterSwordRouteCommandServiceTest)
    dependsOn(spinSlashAttackMetadataTest)
    dependsOn(genericDamageShadowComparatorTest)
    dependsOn(spinSlashDamageShadowRuntimeTest)
    dependsOn(damageShadowCommandRoutingTest)
    dependsOn(mobEditorFoundationTest)
    dependsOn(mobEditorV2ProtocolTest)
    dependsOn(shutdownSequenceTest)
    dependsOn(featureFlagServiceTest)
    dependsOn(schemaVersionRegistryTest)
    dependsOn(betaContractPresenceTest)
    dependsOn(trackDTransactionFoundationTest)
    dependsOn(fireElementEngineTest)
    dependsOn(iceElementEngineTest)
    dependsOn(lightningElementEngineTest)
    dependsOn(equipmentAndModFoundationTest)
    dependsOn(legacyItemCompatibilityTest)
    dependsOn(playerProgressDomainTest)
    dependsOn(playerProgressRepositoryTest)
    dependsOn(playerPersistenceCoordinatorTest)
    dependsOn(wave1IntegratedFoundationTest)
    dependsOn(wave2OwnerDecisionContractTest)
    dependsOn(trackFPartyQuestRewardFoundationTest)
    dependsOn(legacyEnhancementCharacterizationTest)
    dependsOn(trackEFoundationTest)
    dependsOn(enhancementTransactionSafetyTest)
    dependsOn(wave2IntegratedFoundationTest)
    dependsOn(betaProtocolFoundationTest)
    dependsOn(mobV2FoundationTest)
    dependsOn(wave3IntegratedFoundationTest)
    dependsOn(betaActivationFoundationTest)
    dependsOn(activationTrack4RuntimeAdapterTest)
    dependsOn(combatElementsActivationRuntimeTest)
    dependsOn(track1ActivationFoundationTest)
    dependsOn(betaActivationWave1IntegratedTest)
    dependsOn(betaActivationWave1ProtocolIntegrationTest)
    dependsOn(betaCapabilityHandshakePreflightTest)
    dependsOn(track2CompatibilityBoundaryTest)
    dependsOn(track2ConfirmedHitPublisherIntegrationTest)
}
