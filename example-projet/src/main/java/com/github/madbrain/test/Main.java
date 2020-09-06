package com.github.madbrain.test;

import com.github.madbrain.messagelib.Message;

public class Main {

    public void process(Message<MyType> message) {
        System.out.println(message.getString("/address/mainStreet"));
    }

}
