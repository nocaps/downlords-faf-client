package com.faforever.client.legacy.gson;

import com.faforever.client.legacy.domain.StatisticsType;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class StatisticsTypeTypeAdapter extends TypeAdapter<StatisticsType> {

  public static final StatisticsTypeTypeAdapter INSTANCE = new StatisticsTypeTypeAdapter();

  private StatisticsTypeTypeAdapter() {

  }

  @Override
  public void write(JsonWriter out, StatisticsType value) throws IOException {
    if (value == null) {
      out.nullValue();
    } else {
      out.value(value.getString());
    }
  }

  @Override
  public StatisticsType read(JsonReader in) throws IOException {
    StatisticsType statisticsType = StatisticsType.fromString(in.nextString());
    if (statisticsType != null) {
      return statisticsType;
    }
    return StatisticsType.UNKNOWN;
  }
}
