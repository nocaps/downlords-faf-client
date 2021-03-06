package com.faforever.client.stats;

import com.faforever.client.legacy.domain.StatisticsType;

import java.util.concurrent.CompletableFuture;

public interface StatisticsService {

  CompletableFuture<PlayerStatisticsMessage> getStatisticsForPlayer(StatisticsType type, String username);

}
