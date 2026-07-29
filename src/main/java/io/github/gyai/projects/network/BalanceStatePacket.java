package io.github.gyai.projects.network;

import io.github.gyai.projects.manager.BalanceTuningManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public record BalanceStatePacket(
        boolean permitted,
        boolean success,
        String message,
        BalanceTuningManager.Snapshot snapshot
) {
    public static final String CHANNEL = "projects:balance_state_v1";

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(BalancePacketIO.VERSION);
            output.writeBoolean(permitted);
            output.writeBoolean(true);
            output.writeLong(snapshot.revision());
            output.writeBoolean(snapshot.dirty());
            output.writeBoolean(success);
            BalancePacketIO.writeString(
                    output, message == null ? "" : message,
                    BalancePacketIO.MAX_STRING_BYTES);
            List<BalanceTuningManager.WeaponValue> weapons =
                    permitted ? snapshot.weapons() : List.of();
            output.writeShort(weapons.size());
            for (var weapon : weapons) {
                BalancePacketIO.writeString(output, weapon.id(), 64);
                BalancePacketIO.writeString(
                        output, weapon.displayName(), 128);
                output.writeDouble(weapon.defaultAttackPower());
                output.writeDouble(weapon.currentAttackPower());
                output.writeDouble(weapon.defaultAttackSpeed());
                output.writeDouble(weapon.currentAttackSpeed());
            }
            List<BalanceTuningManager.SkillValue> skills =
                    permitted ? snapshot.skills() : List.of();
            output.writeShort(skills.size());
            for (var skill : skills) {
                BalancePacketIO.writeString(output, skill.id(), 64);
                BalancePacketIO.writeString(
                        output, skill.displayName(), 128);
                output.writeBoolean(true);
                output.writeDouble(skill.defaultBaseDamage());
                output.writeDouble(skill.currentBaseDamage());
                output.writeBoolean(true);
                output.writeDouble(skill.defaultScaling());
                output.writeDouble(skill.currentScaling());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode balance state", exception);
        }
    }
}
