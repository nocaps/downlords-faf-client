package com.faforever.client.legacy;

import com.faforever.client.legacy.gson.GameStateTypeAdapter;
import com.faforever.client.legacy.gson.GameTypeTypeAdapter;
import com.faforever.client.legacy.message.GameStatus;
import com.faforever.client.legacy.message.GameType;
import com.faforever.client.legacy.message.ServerWritable;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

/**
 *
 */
public class ServerWriter implements Closeable {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final QStreamWriter writer;
  private final Gson gson;
  private boolean appendUsername;
  private boolean appendSession;
  private CharSequence username;
  private CharSequence session;

  public ServerWriter(OutputStream outputStream) throws IOException {
    writer = new QStreamWriter(new DataOutputStream(new BufferedOutputStream(outputStream)));
    gson = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(GameType.class, new GameTypeTypeAdapter())
        .registerTypeAdapter(GameStatus.class, new GameStateTypeAdapter())
        .create();
  }

  public void setAppendUsername(boolean appendUsername) {
    this.appendUsername = appendUsername;
  }

  public void setAppendSession(boolean appendSession) {
    this.appendSession = appendSession;
  }

  public void setUsername(CharSequence username) {
    this.username = username;
  }

  public void setSession(CharSequence session) {
    this.session = session;
  }

  public void write(ServerWritable serverWritable) {
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      Writer stringWriter = new StringWriter();
      serverWritable.write(gson, stringWriter);

      QStreamWriter qStreamWriter = new QStreamWriter(byteArrayOutputStream);
      qStreamWriter.appendQString(stringWriter.toString());

      if (appendUsername) {
        qStreamWriter.appendQString(username);
      }
      if (appendSession) {
        qStreamWriter.appendQString(session);
      }

      byte[] byteArray = byteArrayOutputStream.toByteArray();

      if (serverWritable.isConfidential()) {
        logger.debug("Writing confidential information to server");
      } else {
        logger.debug("Writing to server: {}", new String(byteArray, StandardCharsets.UTF_16BE));
      }

      synchronized (writer) {
        writer.append(byteArray);
        writer.flush();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}