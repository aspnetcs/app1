package com.webchat.platformapi.ai.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.voice-chat")
public class VoiceChatProperties {

    private boolean enabled = false;
    private int maxDurationSeconds = 60;
    private long maxUploadBytes = 5 * 1024 * 1024;
    private String sttModel = "whisper-1";
    private String ttsModel = "tts-1";
    private String ttsVoice = "alloy";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxDurationSeconds() { return maxDurationSeconds; }
    public void setMaxDurationSeconds(int maxDurationSeconds) { this.maxDurationSeconds = maxDurationSeconds; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public String getSttModel() { return sttModel; }
    public void setSttModel(String sttModel) { this.sttModel = sttModel; }
    public String getTtsModel() { return ttsModel; }
    public void setTtsModel(String ttsModel) { this.ttsModel = ttsModel; }
    public String getTtsVoice() { return ttsVoice; }
    public void setTtsVoice(String ttsVoice) { this.ttsVoice = ttsVoice; }
}
