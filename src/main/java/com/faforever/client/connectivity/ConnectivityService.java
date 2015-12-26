package com.faforever.client.connectivity;

import javafx.beans.property.ReadOnlyObjectProperty;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public interface ConnectivityService {

  CompletableFuture<Void> checkConnectivity();

  ReadOnlyObjectProperty<ConnectivityState> connectivityStateProperty();

  ConnectivityState getConnectivityState();

  InetSocketAddress getExternalSocketAddress();

  InetSocketAddress getRelayAddress();
}