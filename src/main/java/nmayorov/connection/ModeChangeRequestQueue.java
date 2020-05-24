package nmayorov.connection;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ModeChangeRequestQueue {
    private final ConcurrentLinkedDeque<ModeChangeRequest> queue;
    private final Selector selector;

    public ModeChangeRequestQueue(Selector selector) {
        queue = new ConcurrentLinkedDeque<>();
        this.selector = selector;
    }

    public void add(ModeChangeRequest request) {
        queue.add(request);
    }

    public void process() {
        // QUEUE에서 ModeChangeRequest 를 하나 가져온다.
        ModeChangeRequest request = queue.poll();

        while (request != null) {

            // 셀렉터에 등록해 둔 SelectionKey 정보를 획득한다.
            // https://palpit.tistory.com/645
            SelectionKey key = request.connection.channel.keyFor(this.selector);

            // QUEUE 에 있는 클라이언트 소켓채널에 operation을 변경한다.
            if (key != null && key.isValid()) {
                key.interestOps(request.ops); // READ를 수행하거나 WRITE 를 수행하거나
            }
            request = queue.poll();
        }
    }
}
