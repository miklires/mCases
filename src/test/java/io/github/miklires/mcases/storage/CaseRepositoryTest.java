package io.github.miklires.mcases.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.miklires.mcases.api.Rarity;
import io.github.miklires.mcases.casebox.CaseDefinition;
import io.github.miklires.mcases.casebox.RewardDefinition;
import io.github.miklires.mcases.casebox.RewardSelector;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class CaseRepositoryTest {
    private static final CaseDefinition CASE = new CaseDefinition("vote", "Vote", 2, List.of(
            new RewardDefinition("coins", "Coins", Rarity.COMMON, 99, "GOLD_NUGGET", List.of("say ok")),
            new RewardDefinition("rank", "Rank", Rarity.RARE, 1, "DIAMOND", List.of("say rare"))));

    @Test void preparingOpenAtomicallyConsumesKeyAndCreatesJournalEntry() {
        try (CaseRepository repository = new CaseRepository("jdbc:h2:mem:open;DB_CLOSE_DELAY=-1")) {
            UUID player = UUID.randomUUID();
            repository.addKeys(player, CASE.id(), 1).join();
            PreparedOpen prepared = repository.prepareOpen(player, CASE, new RewardSelector(new ZeroRandom())).join();
            assertEquals(0, repository.keyBalance(player, CASE.id()).join());
            assertEquals(prepared.transactionId(), repository.pending(player).join().getFirst().transactionId());
            repository.markDelivered(prepared.transactionId()).join();
            assertEquals(List.of(), repository.pending(player).join());
        }
    }

    @Test void openingWithoutKeyDoesNotCreateReward() {
        try (CaseRepository repository = new CaseRepository("jdbc:h2:mem:no-key;DB_CLOSE_DELAY=-1")) {
            UUID player = UUID.randomUUID();
            CompletionException exception = assertThrows(CompletionException.class,
                    () -> repository.prepareOpen(player, CASE, new RewardSelector(new ZeroRandom())).join());
            assertEquals(NoKeyException.class, exception.getCause().getClass());
            assertEquals(List.of(), repository.pending(player).join());
        }
    }

    @Test void pityStateSurvivesAcrossOpenings() {
        try (CaseRepository repository = new CaseRepository("jdbc:h2:mem:pity;DB_CLOSE_DELAY=-1")) {
            UUID player = UUID.randomUUID();
            repository.addKeys(player, CASE.id(), 2).join();
            assertEquals("coins", repository.prepareOpen(player, CASE, new RewardSelector(new ZeroRandom())).join().reward().id());
            assertEquals("rank", repository.prepareOpen(player, CASE, new RewardSelector(new ZeroRandom())).join().reward().id());
        }
    }

    private static final class ZeroRandom implements RandomGenerator {
        @Override public long nextLong() { return 0; }
        @Override public double nextDouble(double bound) { return 0; }
    }
}
