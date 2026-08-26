package core.proxy;

import core.queue.ByteQueue;

import java.io.IOException;
import java.util.Queue;

public interface ByteConsumer {
    void consume(ByteQueue arr) throws IOException;
}
