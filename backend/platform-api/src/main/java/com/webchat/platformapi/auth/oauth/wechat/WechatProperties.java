package com.webchat.platformapi.auth.oauth.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    private String defaultBrand;
    private Map<String, WechatApp> apps = new HashMap<>();

    public String getDefaultBrand() {
        return defaultBrand;
    }

    public void setDefaultBrand(String defaultBrand) {
        this.defaultBrand = defaultBrand;
    }

    public Map<String, WechatApp> getApps() {
        return apps;
    }

    public void setApps(Map<String, WechatApp> apps) {
        this.apps = apps;
    }

    public static class WechatApp {
        private String appid;
        private String secret;

        public String getAppid() {
            return appid;
        }

        public void setAppid(String appid) {
            this.appid = appid;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
