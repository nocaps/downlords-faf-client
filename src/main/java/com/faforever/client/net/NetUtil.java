package com.faforever.client.net;

import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class NetUtil {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private NetUtil() {
    throw new AssertionError("Not instantiatable");
  }

  /**
   * Reads the specified socket and processes them using the specified consumer.
   *
   * @param executor the {@link Executor} to run the background thread in
   */
  public static void readSocket(Executor executor, final DatagramSocket socket, Consumer<DatagramPacket> consumer) {
    CompletableFuture.runAsync(() -> {
      byte[] buffer = new byte[1500];
      DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);

      try {
        while (!socket.isClosed()) {
          socket.receive(datagramPacket);

          // Passing the original datagramPacket can cause locks
          byte[] payload = new byte[datagramPacket.getLength()];
          System.arraycopy(datagramPacket.getData(), 0, payload, 0, datagramPacket.getLength());

          DatagramPacket packetCopy = new DatagramPacket(payload, payload.length);
          packetCopy.setSocketAddress(datagramPacket.getSocketAddress());
          consumer.accept(packetCopy);
        }
      } catch (SocketException e) {
        // Ignore
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        IOUtils.closeQuietly(socket);
      }
    }, executor).whenComplete((aVoid, throwable) -> {
      if (throwable != null) {
        logger.warn("Exception while forwarding socket: " + socket.getLocalSocketAddress(), throwable);
      }
    });
  }
}
