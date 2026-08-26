package io.github.miklires.mcases.storage;

import io.github.miklires.mcases.casebox.RewardDefinition;
import java.util.UUID;

public record PreparedOpen(UUID transactionId, UUID playerId, String caseId, RewardDefinition reward) {}
