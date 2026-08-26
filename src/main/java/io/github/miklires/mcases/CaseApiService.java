package io.github.miklires.mcases;

import io.github.miklires.mcases.api.MCasesApi;
import io.github.miklires.mcases.api.RewardView;
import io.github.miklires.mcases.casebox.CaseDefinition;
import io.github.miklires.mcases.casebox.CaseService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class CaseApiService implements MCasesApi {
    private final CaseService service;
    CaseApiService(CaseService service) { this.service = service; }
    @Override public List<String> caseIds() { return List.copyOf(service.catalog().all().keySet()); }
    @Override public List<RewardView> rewards(String caseId) {
        CaseDefinition definition = service.catalog().find(caseId).orElseThrow(() -> new IllegalArgumentException("Unknown case: " + caseId));
        double total = definition.rewards().stream().mapToDouble(item -> item.weight()).sum();
        return definition.rewards().stream().map(item -> new RewardView(
                item.id(), item.displayName(), item.rarity(), item.weight() / total)).toList();
    }
    @Override public CompletableFuture<Integer> keyBalance(UUID playerId, String caseId) { return service.balance(playerId, caseId); }
    @Override public CompletableFuture<Void> giveKeys(UUID playerId, String caseId, int amount) {
        if (amount < 1) throw new IllegalArgumentException("Amount must be positive");
        return service.addKeys(playerId, caseId, amount);
    }
}
