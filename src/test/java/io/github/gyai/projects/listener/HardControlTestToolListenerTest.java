package io.github.gyai.projects.listener;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-contract coverage for the tester's direct-attack boundary. */
public final class HardControlTestToolListenerTest {
    public static void main(String[] args) throws Exception {
        serviceManagedDamageReturnsBeforeTesterMutation();
        pluginSuppliesDamageService();
        System.out.println("HardControlTestToolListenerTest passed");
    }

    private static void serviceManagedDamageReturnsBeforeTesterMutation()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/listener/"
                        + "HardControlTestToolListener.java"));
        assert source.contains("private final DamageService damageService;");
        assert source.contains("DamageService damageService");

        int attackHandler = source.indexOf("public void onAttack(");
        int nextMethod = source.indexOf("private void showCustomMonsterOnly", attackHandler);
        assert attackHandler >= 0;
        assert nextMethod > attackHandler;
        String attackSource = source.substring(attackHandler, nextMethod);
        int guard = attackSource.indexOf("damageService.isApplying(player, target)");
        int returnAfterGuard = attackSource.indexOf("return;", guard);
        int cancellation = attackSource.indexOf("event.setCancelled(true);");
        int zeroDamage = attackSource.indexOf("event.setDamage(0);");
        assert guard >= 0;
        assert returnAfterGuard > guard;
        assert guard < cancellation;
        assert guard < zeroDamage;
        assert returnAfterGuard < cancellation;
    }

    private static void pluginSuppliesDamageService() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java"));
        int registration = source.indexOf("new HardControlTestToolListener(");
        assert registration >= 0;
        int registrationEnd = source.indexOf("), this);", registration);
        assert registrationEnd > registration;
        String arguments = source.substring(registration, registrationEnd);
        assert arguments.contains("hardControlTestTool");
        assert arguments.contains("crowdControlManager");
        assert arguments.contains("monsterManager");
        assert arguments.contains("damageService");
    }
}
