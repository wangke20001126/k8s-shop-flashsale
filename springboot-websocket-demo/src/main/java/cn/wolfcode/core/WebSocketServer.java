package cn.wolfcode.core;

import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/{uuid}")
@Component
public class WebSocketServer {

    public static ConcurrentHashMap<String, Session> clients = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("uuid") String uuid) {
        System.out.println("新客户端连接===> uuid=" + uuid);

        clients.forEach((k, v) -> {
            try {
                String data = "{\"uuid\":\"[系统通知]\", \"data\": \"用户【" + k + "】上线了...\"}";
                v.getBasicRemote().sendText(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        clients.put(uuid, session);
    }

    @OnMessage
    public void onMessage(@PathParam("uuid") String uuid, String message) {
        System.out.println("新客户端消息===> uuid=" + uuid + ", message=" + message);
        clients.forEach((k, v) -> {
            if (!uuid.equals(k)) {
                try {
                    String data = "{\"uuid\":\"" + k + "\", \"data\": \"" + message + "\"}";
                    v.getBasicRemote().sendText(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @OnClose
    public void onClose(@PathParam("uuid") String uuid) {
        System.out.println("客户端关闭连接===> uuid=" + uuid);
        clients.remove(uuid);
    }

    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }
}
