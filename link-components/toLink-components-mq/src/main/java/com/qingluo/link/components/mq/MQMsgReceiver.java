package com.qingluo.link.components.mq;

/**
 * Framework-facing raw message receiver contract.
 */
public interface MQMsgReceiver {

    void receive(String msg);
}
