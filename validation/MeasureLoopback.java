package validation;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Locale;
import com.threerings.io.r;
import com.threerings.io.s;
import com.threerings.io.v;
import com.threerings.io.w;
import com.threerings.presents.net.PingRequest;
import com.threerings.presents.net.PongResponse;
import com.threerings.presents.net.Transport;

/** Actual TCP Ping/Pong round trips; excludes neither the server nor its writer queue. */
public final class MeasureLoopback {
    public static void main(String[] args) throws Exception {
        System.setProperty("com.threerings.io.enumPolicy", "ORDINAL");
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])));
             Selector selector = Selector.open()) {
            channel.socket().setTcpNoDelay(true);
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            s sink = new s(); w output = new w(sink);
            r source = new r(); v input = new v(source);
            double[] samples = new double[200];
            for (int index = -30; index < samples.length; index++) {
                PingRequest ping = new PingRequest(Transport.arz);
                ping.c(Transport.arz);
                long start = System.nanoTime();
                output.bf(ping); output.flush();
                ByteBuffer buffer = sink.sH();
                while (buffer.hasRemaining()) channel.write(buffer);
                sink.sI();
                while (!source.a(channel)) {
                    if (System.nanoTime()-start > 5_000_000_000L) throw new AssertionError("Ping timeout");
                    selector.select(1000); selector.selectedKeys().clear();
                }
                Object reply = input.qe();
                double elapsed = (System.nanoTime()-start)/1e6;
                if (!(reply instanceof PongResponse)) throw new AssertionError("Expected PongResponse: " + reply);
                if (index >= 0) samples[index] = elapsed;
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT,
                "[LOCAL][RTT] samples=%d minMs=%.3f medianMs=%.3f p95Ms=%.3f maxMs=%.3f%n",
                samples.length, samples[0], samples[samples.length/2], samples[(samples.length-1)*95/100], samples[samples.length-1]);
        }
    }
}
