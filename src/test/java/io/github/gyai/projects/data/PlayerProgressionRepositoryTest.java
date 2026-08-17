package io.github.gyai.projects.data;

import io.github.gyai.projects.player.StatType;
import java.nio.file.Files;
import java.util.UUID;

public final class PlayerProgressionRepositoryTest {
    public static void main(String[] args) throws Exception {
        var root=Files.createTempDirectory("projects-player-"); var repo=new PlayerProgressionRepository(root); var id=UUID.randomUUID();
        var snapshot=new PlayerProgressionSnapshot(1,id,12,44,java.util.Map.of(StatType.PHYSICAL_ATTACK_FLAT,3.5)); repo.save(snapshot);
        assert repo.load(id).orElseThrow().equals(snapshot);
        Files.writeString(root.resolve(id+".properties"),"schema=99\nuuid="+id+"\n"); assert repo.load(id).isEmpty(); assert Files.exists(root.resolve(id+".properties.unknown"));
        System.out.println("PLAYER_PROGRESSION_REPOSITORY_PASS: atomic round trip unknown-version isolation");
    }
}
