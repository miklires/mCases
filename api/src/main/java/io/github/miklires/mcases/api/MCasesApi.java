package io.github.miklires.mcases.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MCasesApi {
    List<String> caseIds();
    List<RewardView> rewards(String caseId);
    CompletableFuture<Integer> keyBalance(UUID playerId, String caseId);
    CompletableFuture<Void> giveKeys(UUID playerId, String caseId, int amount);
}
