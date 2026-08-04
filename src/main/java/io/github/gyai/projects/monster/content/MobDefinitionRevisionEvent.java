package io.github.gyai.projects.monster.content;

import java.time.Instant;

public record MobDefinitionRevisionEvent(String mobId, long previousRevision,
                                         long currentRevision, Instant occurredAt) { }
