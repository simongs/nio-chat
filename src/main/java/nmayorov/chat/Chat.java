package nmayorov.chat;

import nmayorov.connection.Connection;
import nmayorov.connection.ConnectionEvent;
import nmayorov.server.Server;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Chat {
    private final Server server;
    private final int newConnectionQueueCapacity;
    private final int connectionEventQueueCapacity;

    public Chat(Server server, int newConnectionQueueCapacity, int connectionEventQueueCapacity) {
        this.server = server;
        this.newConnectionQueueCapacity = newConnectionQueueCapacity;
        this.connectionEventQueueCapacity = connectionEventQueueCapacity;
    }

    public void start(int numProcessorThreads) throws IOException {
        ArrayBlockingQueue<Connection> newConnectionQueue = new ArrayBlockingQueue<>(newConnectionQueueCapacity);
        ArrayBlockingQueue<ConnectionEvent> connectionEventQueue
                = new ArrayBlockingQueue<>(connectionEventQueueCapacity);


        // 서버소켓채널을 5000번으로 띄우고
        // SelectionKey 를 처리할 Thread를 하나 띄웁니다.
        server.bindConnectionQueues(newConnectionQueue, connectionEventQueue);
        server.start();

        // 신규 커넥션을 감지할 스레드도 하나 띄웁니다.
        // 신규 커넥션이 있다면 이름을 물어보는 작업을 합니다. (WRITE 작업)
        ConnectionAcceptor connectionAcceptor = new ConnectionAcceptor(newConnectionQueue);
        Thread connectionAcceptorThread = new Thread(connectionAcceptor, "ConnectionAcceptor");
        connectionAcceptorThread.start();


        ConcurrentHashMap<String, Connection> knownConnections = new ConcurrentHashMap<>();
        for (int t = 0; t < numProcessorThreads; ++t) {
            ConnectionProcessor connectionProcessor = new ConnectionProcessor(connectionEventQueue, knownConnections);
            Thread connectionProcessorThread = new Thread(connectionProcessor, "ConnectionProcessor-" + t);
            connectionProcessorThread.start();
        }
    }
}
