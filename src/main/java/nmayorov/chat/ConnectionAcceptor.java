package nmayorov.chat;

import nmayorov.connection.Connection;
import nmayorov.message.ChatMessageBuffer;
import nmayorov.message.NameRequest;
import nmayorov.message.ServerText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

class ConnectionAcceptor implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ConnectionAcceptor.class);

    private final ArrayBlockingQueue<Connection> newConnections;
    ConnectionAcceptor(ArrayBlockingQueue<Connection> newConnections) {
        this.newConnections = newConnections;
    }

    @Override
    public void run() {
        Connection connection;
        while (true) {
            log.info("언제마다 도는 것인가?");
            try {
                connection = newConnections.take(); // 블록킹 메소드인가 보다;
            } catch (InterruptedException e) {
                log.warn("Interrupting while taking connection from queue", e);
                continue;
            }
            connection.messageBuffer = new ChatMessageBuffer();
            connection.write(new ServerText("Enter your name.").getBytes());
            connection.write(new NameRequest().getBytes());
        }
    }
}
