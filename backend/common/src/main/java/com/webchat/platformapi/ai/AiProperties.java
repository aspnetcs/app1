package com.webchat.platformapi.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * Master key used to encrypt upstream API keys in DB.
     * Supports "value-or-file": if this value points to an existing file, file contents will be used.
     */
    private String masterKey;

    private Ssrf ssrf = new Ssrf();

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public Ssrf getSsrf() {
        return ssrf;
    }

    public void setSsrf(Ssrf ssrf) {
        this.ssrf = ssrf;
    }

    public static class Ssrf {
        /**
         * Allowed upstream hosts, e.g. api.openai.com.
         * Use "*.example.com" for suffix match.
         * Empty list means "allow none" for safety.
         */
        private List<String> allowlist = new ArrayList<>();

        /**
         * Allow upstream host to resolve to private IP ranges (e.g. Docker network).
         */
        private boolean allowPrivate = false;

        /**
         * Allow http scheme for allowlisted hosts.
         */
        private boolean allowHttp = false;

        /**
         * Allow http://localhost (and 127.0.0.1) for local development.
         */
        private boolean allowHttpLocalhost = true;

        public List<String> getAllowlist() {
            return allowlist;
        }

        public void setAllowlist(List<String> allowlist) {
            this.allowlist = allowlist == null ? new ArrayList<>() : allowlist;
        }

        public boolean isAllowPrivate() {
            return allowPrivate;
        }

        public void setAllowPrivate(boolean allowPrivate) {
            this.allowPrivate = allowPrivate;
        }

        public boolean isAllowHttp() {
            return allowHttp;
        }

        public void setAllowHttp(boolean allowHttp) {
            this.allowHttp = allowHttp;
        }

        public boolean isAllowHttpLocalhost() {
            return allowHttpLocalhost;
        }

        public void setAllowHttpLocalhost(boolean allowHttpLocalhost) {
            this.allowHttpLocalhost = allowHttpLocalhost;
        }
    }
}

