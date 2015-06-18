package com.faforever.client.game;

import com.faforever.client.legacy.OnGameInfoListener;
import com.faforever.client.legacy.OnGameTypeInfoListener;
import com.faforever.client.legacy.ServerAccessor;
import com.faforever.client.legacy.domain.GameLaunchInfo;
import com.faforever.client.legacy.domain.GameTypeInfo;
import com.faforever.client.legacy.proxy.Proxy;
import com.faforever.client.map.MapService;
import com.faforever.client.patch.PatchService;
import com.faforever.client.supcom.ForgedAllianceService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.Callback;
import com.faforever.client.util.ConcurrentUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameServiceImpl implements GameService, OnGameTypeInfoListener {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired
  ServerAccessor serverAccessor;

  @Autowired
  UserService userService;

  @Autowired
  ForgedAllianceService forgedAllianceService;

  @Autowired
  PatchService patchService;

  @Autowired
  MapService mapService;

  @Autowired
  Proxy proxy;

  private ObservableMap<String, GameTypeBean> gameTypeBeans;

  public GameServiceImpl() {
    gameTypeBeans = FXCollections.observableHashMap();
  }

  @PostConstruct
  void postConstruct() {
    serverAccessor.addOnGameTypeInfoListener(this);
  }

  @Override
  public void publishPotentialPlayer() {
    String username = userService.getUsername();
//    serverAccessor.publishPotentialPlayer(username);
  }

  @Override
  public void addOnGameInfoListener(OnGameInfoListener listener) {
    serverAccessor.addOnGameInfoListener(listener);
  }

  @Override
  public void hostGame(NewGameInfo newGameInfo, Callback<Void> callback) {
    cancelLadderSearch();
    updateGameIfNecessary(newGameInfo.getMod(), new Callback<Void>() {
      @Override
      public void success(Void result) {
        serverAccessor.requestNewGame(newGameInfo, gameLaunchCallback(callback));
      }

      @Override
      public void error(Throwable e) {
        callback.error(e);
      }
    });
  }

  private void updateGameIfNecessary(String modName, Callback<Void> callback) {
    callback.success(null);
  }

  @Override
  public void cancelLadderSearch() {
    logger.warn("Cancelling ladder search has not yet been implemented");
  }

  @Override
  public void joinGame(GameInfoBean gameInfoBean, String password, Callback<Void> callback) {
    logger.info("Joining game {} ({})", gameInfoBean.getTitle(), gameInfoBean.getUid());

    cancelLadderSearch();

    Callback<Void> mapDownloadCallback = new Callback<Void>() {
      @Override
      public void success(Void result) {
        serverAccessor.requestJoinGame(gameInfoBean, password, gameLaunchCallback(callback));
      }

      @Override
      public void error(Throwable e) {

      }
    };

    updateGameIfNecessary(gameInfoBean.getFeaturedMod(), new Callback<Void>() {
      @Override
      public void success(Void result) {
        downloadMapIfNecessary(gameInfoBean.getMapName(), mapDownloadCallback);
      }

      @Override
      public void error(Throwable e) {
        callback.error(e);
      }
    });
  }

  private void downloadMapIfNecessary(String mapName, Callback<Void> callback) {
    if (mapService.isAvailable(mapName)) {
      callback.success(null);
      return;
    }

    mapService.download(mapName, callback);
  }

  @Override
  public List<GameTypeBean> getGameTypes() {
    return new ArrayList<>(gameTypeBeans.values());
  }

  @Override
  public void addOnGameTypeInfoListener(MapChangeListener<String, GameTypeBean> changeListener) {
    gameTypeBeans.addListener(changeListener);
  }

  private Callback<GameLaunchInfo> gameLaunchCallback(final Callback<Void> callback) {
    return new Callback<GameLaunchInfo>() {
      @Override
      public void success(GameLaunchInfo gameLaunchInfo) {
        List<String> args = fixMalformedArgs(gameLaunchInfo.args);
        try {
          Process process = forgedAllianceService.startGame(gameLaunchInfo.uid, gameLaunchInfo.mod, args);
          serverAccessor.notifyGameStarted();
          waitForProcessTerminationInBackground(process);

          Platform.runLater(() -> callback.success(null));
        } catch (Exception e) {
          Platform.runLater(() -> callback.error(e));
        }
      }

      @Override
      public void error(Throwable e) {
        // FIXME implement
        logger.warn("Could not create game", e);
      }
    };
  }

  private void waitForProcessTerminationInBackground(Process process) {
    ConcurrentUtil.executeInBackground(new Task<Void>() {
      @Override
      protected Void call() throws Exception {
        int exitCode = process.waitFor();
        logger.info("Forged Alliance terminated with exit code {}", exitCode);
        proxy.close();
        serverAccessor.notifyGameTerminated();
        return null;
      }
    });
  }

  /**
   * A correct argument list looks like ["/ratingcolor", "d8d8d8d8", "/numgames", "236"]. However, the FAF server sends
   * it as ["/ratingcolor d8d8d8d8", "/numgames 236"]. This method fixes this.
   */
  private List<String> fixMalformedArgs(List<String> gameLaunchMessage) {
    ArrayList<String> fixedArgs = new ArrayList<>();

    for (String combinedArg : gameLaunchMessage) {
      String[] split = combinedArg.split(" ");

      Collections.addAll(fixedArgs, split);
    }
    return fixedArgs;
  }

  @Override
  public void onGameTypeInfo(GameTypeInfo gameTypeInfo) {
    if (!gameTypeInfo.host || !gameTypeInfo.live || gameTypeBeans.containsKey(gameTypeInfo.name)) {
      return;
    }

    gameTypeBeans.put(gameTypeInfo.name, new GameTypeBean(gameTypeInfo));
  }
}
