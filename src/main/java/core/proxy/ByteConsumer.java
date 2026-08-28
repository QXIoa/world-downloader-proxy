package core.proxy;

import core.queue.ByteQueue;

import java.io.IOException;

public interface ByteConsumer {
    void consume(ByteQueue arr) throws IOException;
}
