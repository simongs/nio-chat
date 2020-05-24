package nmayorov.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class NioServer extends Server {

    private static Logger LOGGER = LoggerFactory.getLogger(NioServer.class);

    public NioServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void start() throws IOException {
        LOGGER.info("Starting server at " + address);

        // NIO의 서버소켓 채널을 오픈한다.
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        // 5000번 포트로 바인딩을 한다.
        serverChannel.bind(address);

        // 해당 서버소켓 채널을 기반으로 Accept()를 처리할 스레드를 생성한다.
        // 이때 커넥션 관리를 위한 QUEUE
        // 커넥션의 이벤트를 관리할 QUEUE 를 전달한다.
        Thread selectionThread = new Thread(new SelectionLoopThread(serverChannel, newConnections, connectionEvents), "SelectionLoop");
        selectionThread.start();
    }
}
