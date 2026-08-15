package com.webchat.platformapi.ai.channel;

public class NoChannelException extends RuntimeException {
    public NoChannelException(String message) {
        super(message);
    }

    public static NoChannelException forModel(String model) {
        return new NoChannelException("no channel for model: " + (model == null ? "" : model));
    }
}

