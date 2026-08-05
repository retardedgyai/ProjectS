plugins {
    java
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

configurations.named("testCompileOnly") {
    extendsFrom(configurations.compileOnly.get())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
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

tasks.test {
    failOnNoDiscoveredTests = false
}

tasks.named("check") {
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
    dependsOn(combatElementsActivationRuntimeTest)
    dependsOn(track1ActivationFoundationTest)
}
