package com.faforever.client.game;

import com.faforever.client.chat.CountryFlagService;
import com.faforever.client.chat.PlayerInfoBean;
import com.faforever.client.i18n.I18n;
import com.faforever.client.util.RatingUtil;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import javax.annotation.Resource;


public class PlayerCardTooltipController {

  @FXML
  Label playerInfo;
  @FXML
  ImageView playerFlag;

  @Resource
  CountryFlagService countryFlagService;
  @Resource
  I18n i18n;

  public void setPlayer(PlayerInfoBean playerInfoBean) {
    playerFlag.setImage(countryFlagService.loadCountryFlag(playerInfoBean.getCountry()));

    String playerInfoLocalized = i18n.get("userInfo.tooltipFormat", playerInfoBean.getUsername(), RatingUtil.getRoundedGlobalRating(playerInfoBean));
    playerInfo.setText(playerInfoLocalized);
  }

  public Node getRoot() {
    return playerInfo;
  }
}
