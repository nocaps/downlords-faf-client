package com.faforever.client.chat;

import com.faforever.client.fx.FxmlLoader;
import com.faforever.client.game.GameService;
import com.faforever.client.legacy.GameStatus;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.user.UserService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.PopupWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;

public class ChatUserControl extends HBox {

  private static final String CLAN_TAG_FORMAT = "[%s]";

  @Autowired
  ApplicationContext applicationContext;

  @Autowired
  FxmlLoader fxmlLoader;

  @Autowired
  AvatarService avatarService;

  @Autowired
  CountryFlagService countryFlagService;

  @Autowired
  ChatController chatController;

  @Autowired
  GameService gameService;

  @Autowired
  PreferencesService preferencesService;

  @Autowired
  UserService userService;

  @Autowired
  ChatService chatService;

  @Autowired
  Environment environment;

  @FXML
  ImageView countryImageView;

  @FXML
  ImageView avatarImageView;

  @FXML
  Label usernameLabel;

  @FXML
  Label clanLabel;

  @FXML
  ImageView statusImageView;

  private PlayerInfoBean playerInfoBean;

  @FXML
  void onContextMenuRequested(ContextMenuEvent event) {
    ChatUserContextMenuController contextMenuController = applicationContext.getBean(ChatUserContextMenuController.class);
    contextMenuController.setPlayerInfoBean(playerInfoBean);
    contextMenuController.getContextMenu().show(getScene().getWindow(), event.getScreenX(), event.getScreenY());
  }

  @FXML
  void onUsernameClicked(MouseEvent mouseEvent) {
    if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) {
      chatController.openPrivateMessageTabForUser(playerInfoBean.getUsername());
    }
  }

  @PostConstruct
  void init() {
    fxmlLoader.loadCustomControl("chat_user_control.fxml", this);
  }

  public PlayerInfoBean getPlayerInfoBean() {
    return playerInfoBean;
  }

  public void setPlayerInfoBean(PlayerInfoBean playerInfoBean) {
    this.playerInfoBean = playerInfoBean;

    configureColor();
    addPrettyColorListener();
    configureCountryImageView();
    configureAvatarImageView();
    configureClanLabel();
    configureGameStatusView();

    usernameLabel.setText(playerInfoBean.getUsername());
  }

  private void configureColor() {
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChatPrefs();
    ObjectProperty<Color> colorProperty = null;
    Color color = null;

    if (playerInfoBean.getModeratorInChannels().size() > 0) {
      colorProperty = chatPrefs.modsChatColorProperty();
    } else if (playerInfoBean.isFriend()) {
      colorProperty = chatPrefs.friendsChatColorProperty();
    } else if (playerInfoBean.isFoe()) {
      colorProperty = chatPrefs.foesChatColorProperty();
    }

    if (!chatPrefs.getPrettyColors() && colorProperty == null) {

      if (playerInfoBean.isChatOnly()) {
        colorProperty = chatPrefs.ircChatColorProperty();
      } else if (userService.getUsername().equals(playerInfoBean.getUsername())) {
        colorProperty = chatPrefs.selfChatColorProperty();
      } else {
        colorProperty = chatPrefs.othersChatColorProperty();
      }

    } else if (chatPrefs.getPrettyColors() && colorProperty == null) {
      color = ColorGeneratorUtil.generatePrettyHexColor();
      ChatUser chatUser = chatService.getChatUserForChannel(environment.getProperty("irc.defaultChannel"), playerInfoBean.getUsername());
      chatUser.setColor(color);
    }

    if (colorProperty != null) {
      //FIXME if there is a listener already will this break?
      color = colorProperty.get();
      addColorListenerToLabels(colorProperty);
    }

    usernameLabel.setTextFill(color);
    clanLabel.setTextFill(color);
  }

  private void addColorListenerToLabels(ObjectProperty<Color> colorProperty) {
    colorProperty.addListener((observable, oldValue, newValue) -> {
      usernameLabel.setTextFill(newValue);
      clanLabel.setTextFill(newValue);
    });
  }

  private void addPrettyColorListener() {
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChatPrefs();
    chatPrefs.prettyColorsProperty().addListener((observable, oldValue, newValue) -> {
      configureColor();
    });
  }

  private void configureCountryImageView() {
    playerInfoBean.countryProperty().addListener((observable, oldValue, newValue) -> {
      Platform.runLater(() -> setCountry(newValue));
    });
    setCountry(playerInfoBean.getCountry());

    Tooltip countryTooltip = new Tooltip(playerInfoBean.getCountry());
    countryTooltip.textProperty().bind(playerInfoBean.countryProperty());

    Tooltip.install(countryImageView, countryTooltip);
  }

  private void configureAvatarImageView() {
    playerInfoBean.avatarUrlProperty().addListener((observable, oldValue, newValue) -> {
      Platform.runLater(() -> setAvatarUrl(newValue));
    });
    setAvatarUrl(playerInfoBean.getAvatarUrl());

    Tooltip avatarTooltip = new Tooltip(playerInfoBean.getAvatarTooltip());
    avatarTooltip.textProperty().bind(playerInfoBean.avatarTooltipProperty());
    avatarTooltip.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);

    Tooltip.install(avatarImageView, avatarTooltip);
  }

  private void configureGameStatusView() {
    setGameStatus(playerInfoBean.getGameStatus());
    playerInfoBean.gameStatusProperty().addListener((observable, oldValue, newValue) -> {
      Platform.runLater(() -> setGameStatus(newValue));
    });

    Tooltip statusTooltip = new Tooltip(playerInfoBean.getGameStatus().toString());
    statusTooltip.textProperty().bind(playerInfoBean.gameStatusProperty().asString());
  }

  private void configureClanLabel() {
    setClanTag(playerInfoBean.getClan());
    playerInfoBean.clanProperty().addListener((observable, oldValue, newValue) -> {
      Platform.runLater(() -> setClanTag(newValue));
    });
  }

  private void setCountry(String country) {
    if (StringUtils.isEmpty(country)) {
      countryImageView.setVisible(false);
    } else {
      countryImageView.setImage(countryFlagService.loadCountryFlag(country));
      countryImageView.setVisible(true);
    }
  }

  private void setAvatarUrl(String avatarUrl) {
    if (StringUtils.isEmpty(avatarUrl)) {
      avatarImageView.setVisible(false);
    } else {
      avatarImageView.setImage(avatarService.loadAvatar(avatarUrl));
      avatarImageView.setVisible(true);
    }
  }

  private void setClanTag(String newValue) {
    if (StringUtils.isEmpty(newValue)) {
      clanLabel.setVisible(false);
    } else {
      clanLabel.setText(String.format(CLAN_TAG_FORMAT, newValue));
      clanLabel.setVisible(true);
    }
  }

  String path = "themes/default/images/chat/";

  public void setGameStatus(GameStatus gameStatus) {
    try {
      switch (gameStatus) {
        case PLAYING:
          statusImageView.setImage(new Image(new ClassPathResource(path + "playing.png").getURL().toString(), true));
          break;
        case HOST:
          statusImageView.setImage(new Image(new ClassPathResource(path + "host.png").getURL().toString(), true));
          break;
        case LOBBY:
          statusImageView.setImage(new Image(new ClassPathResource(path + "lobby.png").getURL().toString(), true));
          break;
        default:
          statusImageView.setImage(new Image(new ClassPathResource(path + "none.png").getURL().toString(), true));
      }
    } catch (IOException e) {
      //fixme log
    }
    statusImageView.setVisible(true);
  }
}
