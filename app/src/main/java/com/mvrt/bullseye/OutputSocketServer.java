package com.mvrt.bullseye;

import com.mvrt.bullseye.util.Notifier;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class OutputSocketServer extends WebSocketServer{

    private int connections = 0;

    public OutputSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    public void start(){
        Notifier.d(getClass(), "Starting Output Socket Server");
        super.start();
    }

    public void stop(){
        try {
            super.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendToAll(byte[] bytes){
        if(connections < 1) return; //quick check for connections (in an attempt to speed it up)
        for(WebSocket conn:connections()){
            conn.send(bytes);
        }
    }

    public void sendToAll(String msg){
        if(connections < 1) return; //quick check for connections (in an attempt to speed it up)
        for(WebSocket conn:connections()){
            conn.send(msg);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Notifier.d(getClass(), "Websocket Connected: " + conn.getLocalSocketAddress().getHostString());
        connections++;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Notifier.d(getClass(), "Websocket Closed: " + conn.getLocalSocketAddress().getHostString() + ", reason: " + reason);
        connections--;
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Notifier.d(getClass(), conn + ": " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }
}
