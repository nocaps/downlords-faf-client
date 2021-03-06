package com.faforever.client.patch;

import com.faforever.client.preferences.PreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public class AbstractPatchService {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Resource
  PreferencesService preferencesService;

  /**
   * Since it's possible that the user has changed or never specified the game path, this method needs to be called
   * every time before any work is done.
   *
   * @return {@code true} if directories are set up correctly
   */
  protected boolean checkDirectories() {
    Path faDirectory = preferencesService.getPreferences().getForgedAlliance().getPath();
    return faDirectory != null;
  }
}
