package nmayorov.app;

import nmayorov.chat.Chat;
import nmayorov.server.NioServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

class StartDemoServer {
    private static Logger log = LoggerFactory.getLogger(StartDemoServer.class);
    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        log.info("TEST");

        NioServer server = new NioServer(new InetSocketAddress(PORT));
        Chat chat = new Chat(server, 10, 100);
        chat.start(1);
    }
}
