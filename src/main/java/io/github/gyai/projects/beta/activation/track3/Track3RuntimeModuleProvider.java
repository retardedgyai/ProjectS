package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.track3.spi.BetaRuntimeModuleProvider;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Publishes Track 3 modules without adding them to BetaRuntimeFactory. */
public final class Track3RuntimeModuleProvider implements BetaRuntimeModuleProvider,
        AutoCloseable {
    private final StagingEconomyService service;
    private final List<Track3RuntimeModule> modules;

    public Track3RuntimeModuleProvider(StagingEconomyService service) {
        this(service, null);
    }

    public Track3RuntimeModuleProvider(
            StagingEconomyService service,
            StagingTransactionRecoveryService recovery
    ) {
        if (service == null) throw new IllegalArgumentException("service is required");
        this.service = service;
        modules = List.of(
                new Track3RuntimeModule(BetaRuntimeModuleId.GATHERING_CRAFTING,
                        StagingEconomyService.OperationGroup.GATHERING_CRAFTING, service, recovery),
                new Track3RuntimeModule(BetaRuntimeModuleId.ENHANCEMENT_REPAIR,
                        StagingEconomyService.OperationGroup.ENHANCEMENT_REPAIR, service, recovery));
    }

    public static Track3RuntimeModuleProvider unregisteredStaging(Clock clock) {
        BoundedStagingInventory inventory = new BoundedStagingInventory();
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(512);
        StagingInventoryTransactionAdapter transactions =
                new StagingInventoryTransactionAdapter(
                        inventory, journal, clock, UUID::randomUUID);
        return new Track3RuntimeModuleProvider(new StagingEconomyService(
                inventory, journal, transactions, new StagingEnhancementOutcomeRegistry()));
    }

    @Override
    public List<? extends BetaRuntimeModule> modules() {
        return modules;
    }

    public List<Track3RuntimeModule> track3Modules() {
        return modules;
    }

    public StagingEconomyService service() {
        return service;
    }

    @Override
    public void close() {
        for (int index = modules.size() - 1; index >= 0; index--) modules.get(index).stop();
        service.close();
    }
}
