package nmayorov.server;

import nmayorov.connection.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Selector의 이벤트들을 처리할 스레드이다.
 */
class SelectionLoopThread implements Runnable {
    private static Logger LOGGER = LoggerFactory.getLogger(SelectionLoopThread.class);


    private final Selector selector; // 등록된 채널에 이벤트가 발생하는지 감지하는 Selector

    private final ArrayBlockingQueue<Connection> newConnections;
    private final ArrayBlockingQueue<ConnectionEvent> connectionEvents;
    private final ModeChangeRequestQueue modeChangeRequestQueue;

    SelectionLoopThread(ServerSocketChannel serverSocketChannel,
                        ArrayBlockingQueue<Connection> newConnections,
                        ArrayBlockingQueue<ConnectionEvent> connectionEvents) throws IOException {
        serverSocketChannel.configureBlocking(false);

        // 셀렉터를 생성한다.
        this.selector = Selector.open();

        // 해당 셀렉터에 클라이언트의 접속을 처리할 서버소켓채널을 등록하면서 ACCEPT 액션에 대해 대기시킨다.
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 커넥션 큐와 커넥션 이벤트 큐를 할당한다.
        this.newConnections = newConnections;
        this.connectionEvents = connectionEvents;

        // READ, WRITE 이벤트에 대한 처리를 위한 QUEUE를 생성한다. (조금더 분석필요)
        this.modeChangeRequestQueue = new ModeChangeRequestQueue(this.selector);
    }

    @Override
    public void run() {
        while (true) {
            LOGGER.info("언제마다 도는 것인가?");

            // 현재 modeChangeRequest 큐에 이벤트가 있다면 처리한다.
            modeChangeRequestQueue.process();

            try {
                int selectCount = selector.select(1000);
            } catch (IOException e) {
                LOGGER.error("Error selecting IO channels, server might be broken", e);
                continue;
            }

            // 작업 처리 준비된 SelectionKey들을 Set 컬렉션으로 얻습니다.
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                // 작업 처리 준비된 SelectionKey를 가져옵니다.
                SelectionKey key = selectedKeys.next();
                // 일단 iteration에서 제거합니다.
                selectedKeys.remove();

                if (!key.isValid()) {
                    continue;
                }



                LOGGER.info("어떤 SelectionKey 정보가 왔을까요? {}", key.readyOps());

                if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                }
            }
        }
    }

    /** 클라이언트의 접속을 처리합니다. */
    private void accept(SelectionKey key) {
        // Accept 이벤트를 걸어놓은 서버소켓채널을 가져옵니다.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel;
        String remoteAddress;
        try {
            // 클라이언트의 접속을 성공함과 동시에 클라이언트와의 연결을 나타내는 SocketChannel을 리턴합니다.
            socketChannel = serverSocketChannel.accept();
            remoteAddress = socketChannel.getRemoteAddress().toString();
        } catch (IOException e) {
            LOGGER.warn("Error accepting connection", e);
            return;
        }

        LOGGER.info("Connection from " + remoteAddress);

        // 해당 접속을 감싼 NioSocketConnection 객체를 생성합니다.
        // 생성과 동시에 여러가지 일들을 처리합니다.
        // 일단 클라이언트 소켓채널을 넌블락킹으로 처리합니다.
        // 그리고 selector에 해당 클라이언트의 READ 액션을 감지하도록 등록합니다.
        // 읽기/쓰기 모드를 관리하는 ModeChangeRequestQueue 도 같이 넘깁니다.
        // 현재 커넥션의 모드를 READ로 설정합니다.
        // 쓰기 버퍼, 읽기 버퍼도 초기화합니다.
        NioSocketConnection connection;
        try {
            connection = new NioSocketConnection(selector, socketChannel, modeChangeRequestQueue);
        } catch (IOException e) {
            LOGGER.warn("Error creating connection from SocketChannel", e);
            return;
        }

        // 신규커넥션 정보에 해당 커넥션 정보를 추가합니다.
        try {
            newConnections.put(connection);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while putting new Connection");
        }
    }

    private void read(SelectionKey key) {
        NioSocketConnection connection = (NioSocketConnection) key.attachment();

        int read;
        try {
            read = connection.readFromChannel();
        } catch (IOException e) {
            read = -1;
        }

        if (read == -1) {
            key.cancel();
            close(connection);
        }

        try {
            connectionEvents.put(new DataReceived(connection));
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while putting DATA ConnectionEvent");
        }
    }

    private void write(SelectionKey key) {
        NioSocketConnection connection = (NioSocketConnection) key.attachment();
        try {
            connection.writeToChannel();
        } catch (IOException e) {
            close(connection);
            return;
        }

        if (connection.shouldClose() && connection.nothingToWrite()) {
            close(connection);
        }
    }

    private void close(NioSocketConnection connection) {
        try {
            connection.channel.close();
        } catch (IOException e) {
            LOGGER.warn("Error closing connection", e);
        }

        try {
            connectionEvents.put(new CloseConnection(connection));
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while putting CLOSE ConnectionEvent");
        }
    }
}
