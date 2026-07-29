package io.github.gyai.projects.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Optional;

public record WarriorLoadoutRequestPacket(Action action) {
    public static final String CHANNEL = "projects:loadout_req_v1";
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAXIMUM_BYTES = 8;

    public static Optional<WarriorLoadoutRequestPacket> decode(
            byte[] payload
    ) {
        if (payload == null
                || payload.length < 2
                || payload.length > MAXIMUM_BYTES) {
            return Optional.empty();
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != PROTOCOL_VERSION) {
                return Optional.empty();
            }
            Action action = Action.fromId(input.readUnsignedByte());
            if (action == null || input.available() != 0) {
                return Optional.empty();
            }
            return Optional.of(new WarriorLoadoutRequestPacket(action));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public enum Action {
        OPEN(0),
        RESET(1);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        private static Action fromId(int id) {
            for (Action action : values()) {
                if (action.id == id) return action;
            }
            return null;
        }
    }
}
