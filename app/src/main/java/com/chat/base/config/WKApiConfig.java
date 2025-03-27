package com.chat.base.config;

import android.text.TextUtils;

import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2019-11-20 10:11
 * api地址
 */
public class WKApiConfig {
    private static final String apiURL = "http://im.virjar.com:8090";
    public static String baseUrl = apiURL + "/v1/";
    public static String baseWebUrl = apiURL + "/web/";


    public static String getAvatarUrl(String uid) {
        return baseUrl + "users/" + uid + "/avatar";
    }

    public static String getGroupUrl(String groupId) {
        return baseUrl + "groups/" + groupId + "/avatar";
    }

    public static String getShowAvatar(String channelID, byte channelType) {
        return channelType == WKChannelType.PERSONAL ? getAvatarUrl(channelID) : getGroupUrl(channelID);
    }

    public static String getShowUrl(String url) {
        if (TextUtils.isEmpty(url) || url.startsWith("http") || url.startsWith("HTTP")) {
            return url;
        } else {
            return baseUrl + url;
        }
    }

}
