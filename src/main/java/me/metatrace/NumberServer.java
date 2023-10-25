package me.metatrace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class NumberServer extends WebSocketServer {

    public NumberServer(int port) {
        super(new InetSocketAddress(port));
    }

    private static final Map<String, WebSocket> ipMap = new ConcurrentHashMap<>();

    private static final Map<BigInteger, String> integers = new ConcurrentHashMap<>();

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) { //данный метод вызывается при открытии апргрейде коннета до WebSocket
        conn.send("Welcome to the server!");
        broadcast("new connection: " + handshake
                .getResourceDescriptor());
        final String ip = findIp(conn);

        if (ipMap.containsKey(ip)) { //проверка на уникальность IP адреса
            broadcast(conn.getRemoteSocketAddress().getAddress() + " user already connected!");
            conn.close();
            final WebSocket originalConn = ipMap.get(conn.getRemoteSocketAddress().getAddress().getHostAddress());
            originalConn.close(); //закрытие сессий с указанным IP адресом
            return;
        } else {
            ipMap.put(findIp(conn), conn);
        }
        System.out.println(ip + " connected to the server!");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        broadcast(conn.getRemoteSocketAddress().getAddress() + " has left the server!");
        System.out.println(conn.getRemoteSocketAddress().getAddress() + " has left the server!");
        ipMap.remove(findIp(conn)); //при закрытии соединения из мапы удаляется текущий IP
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        final ObjectMapper mapper = new ObjectMapper();
        BigInteger number = generate();
        if (integers.containsKey(number)) { //проверка на дубли сгенерированных чисел (фактически дублей быть не может, но для данного тестового кейса оставляем проверку)
            number = generate();
        } else {
            integers.put(number, "");
        }
        final Message outcomeMessage = buildMessage(number);
        try {
            final String broadcastedMessage = mapper.writeValueAsString(outcomeMessage);
            System.out.println("Responded message: " + broadcastedMessage);
            broadcast(broadcastedMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        broadcast(message.array());
        System.out.println(conn + ": " + message);
    }


    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started!");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        int port = 8080;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception ex) {
            System.out.println(ex);
        }
        NumberServer s = new NumberServer(port);
        s.start();
        System.out.println("Server started on port: " + s.getPort());

        BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String in = sysin.readLine();
            s.broadcast(in);
            if (in.equals("exit")) {
                s.stop(1000);
                break;
            }
        }
    }

    private Message buildMessage(BigInteger number) {
        final Message message = new Message();
        message.setNumber(number);
        return message;
    }

    public BigInteger generate() {
        BigInteger number = new BigInteger(1024, new Random());
        number = number.setBit(0);
        return number;
    }

    private String findIp(WebSocket conn) {
        return conn.getRemoteSocketAddress().getAddress().getHostAddress();
    }
}