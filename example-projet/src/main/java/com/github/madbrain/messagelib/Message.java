package com.github.madbrain.messagelib;

public interface Message<T extends MessageType> {
    String getString(String spec);
}
