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
    classpath = sourceSets.test.get().runtimeClasspath
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

val mobEditorFoundationTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath +
            sourceSets.main.get().compileClasspath
    mainClass.set("io.github.gyai.projects.monster.editor.MobEditorFoundationTest")
    jvmArgs("-ea")
}

tasks.test {
    failOnNoDiscoveredTests = false
}

tasks.named("check") {
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
    dependsOn(mobEditorFoundationTest)
}
