package io.github.miklires.mcases.api;

import java.util.UUID;

public record PreparedReward(UUID transactionId, UUID playerId, String caseId, String rewardId) {}
