package net.defraine.wstcp.client;

import net.defraine.wstcp.Scrambler;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

public class WSTcpClient extends Endpoint {
    protected PrintStream log;
    protected InputStream is;
    protected OutputStream os;
    protected Session session;
    protected AtomicInteger errors = new AtomicInteger(0);
    protected CountDownLatch toFinish = new CountDownLatch(1);
    protected Scrambler sendScrambler;
    protected Scrambler recvScrambler;

    public WSTcpClient(PrintStream log, InputStream is, OutputStream os, Long optKey) {
        this.log = log;
        this.is = is;
        this.os = os;
        if (optKey != null) {
            long key = optKey.longValue();
            sendScrambler = new Scrambler(key);
            recvScrambler = new Scrambler(~key);
        }
    }

    protected Thread reader = new Thread() {
        public static final int bufferSize = 4*1024;

        @Override
        public void run() {
            RemoteEndpoint.Basic remote = session.getBasicRemote();
            try {
                byte[] buf = new byte[bufferSize];
                while (true) {
                    int bytesRead;
                    try {
                        bytesRead = is.read(buf);
                    } catch (IOException e) {
                        log.println("IO error reading input: " + e.getMessage());
                        errors.incrementAndGet();
                        break;
                    }
                    if (!session.isOpen()) {
                        if (bytesRead > 0) {
                            log.println("Error: remote side unexpectedly closed");
                            errors.incrementAndGet();
                        }
                        break;
                    }
                    if (bytesRead != -1) {
                        // send a message when something to send
                        if (bytesRead > 0) {
                            if (sendScrambler != null)
                                sendScrambler.scramble(buf, 0, bytesRead);
                            remote.sendBinary(ByteBuffer.wrap(buf, 0, bytesRead));
                        }
                    } else {
                        // end-of-stream, send empty message marker, then terminate
                        remote.sendBinary(ByteBuffer.wrap(buf, 0, 0));
                        break;
                    }
                }
            } catch (IOException e) {
                log.println("IO error on websocket: " + e.getMessage());
                errors.incrementAndGet();
            }
        }
    };

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        session.addMessageHandler(byte[].class, new MessageHandler.Partial<byte[]>() {
            private int totalLength = 0;

            @Override
            public void onMessage(byte[] msg, boolean last) {
                try {
                    if (msg.length > 0) {
                        if (recvScrambler != null)
                            recvScrambler.scramble(msg, 0, msg.length);
                        os.write(msg);
                        totalLength += msg.length;
                    }
                    if (last) {
                        if (totalLength > 0) {
                            os.flush();
                            totalLength = 0;
                        } else {
                            os.close();
                        }
                    }
                } catch (IOException e) {
                    log.println("Error: could not output: " + e.getMessage());
                    errors.incrementAndGet();
                }
            }
        });
        reader.start();
    }

    @Override
    public void onClose(Session session, CloseReason c) {
        if (c.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE) {
            log.println("Websocket closed: " + c.getReasonPhrase());
            errors.incrementAndGet();
        }
        toFinish.countDown();
    }

    public void waitUntilDone() throws InterruptedException {
        toFinish.await();
        // note: deliberately not joining with reader;
        // we have no good way to stop reader from onClose, so we leave it running and just exit
    }

    public int getErrorCount() {
        return errors.get();
    }

    @SuppressWarnings("serial")
    protected static class FatalError extends Exception {
        public FatalError(String msg) {
            super(msg);
        }
    }

    protected static boolean scramble = false;
    protected static String endPointURL;

    protected static boolean parseArgs(String[] args) {
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            if (arg.equals("-scramble")) {
                scramble = true;
            } else if (arg.equals("-no-scramble")) {
                scramble = false;
            } else if (!arg.isEmpty() && arg.charAt(0) == '-') {
                System.err.println("Error: unrecognized command line option: " + arg);
                return false;
            } else {
                if (endPointURL == null)
                    endPointURL = arg;
                else {
                    System.err.println("Error: too many arguments");
                    return false;
                }
            }
        }
        if (endPointURL == null) {
            System.err.println("Error: too few arguments");
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        if (!parseArgs(args)) {
            System.err.println("Usage: " + WSTcpClient.class.getName() + " [-scramble|-no-scramble] <endpoint-url>");
            System.exit(1);
        }
        try {
            URI uri;
            try {
                uri = new URI(endPointURL);
            } catch (URISyntaxException e) {
                throw new FatalError("invalid endpoint url: " + e.getMessage());
            }
            if (!uri.isAbsolute() || uri.getSchemeSpecificPart().charAt(0) != '/') {
                throw new FatalError("endpoint url is not of the proper form");
            }
            Long optKey;
            if (scramble) {
                long key = System.nanoTime();
                String query = uri.getQuery();
                if (query != null)
                    query = query + "&key=" + Long.toHexString(key);
                else
                    query = "key=" + Long.toHexString(key);
                try {
                    uri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), query, uri.getFragment());
                } catch (URISyntaxException e) {
                    throw new AssertionError("URI should be valid", e);
                }
                optKey = Long.valueOf(key);
            } else
                optKey = null;

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            WSTcpClient endpoint = new WSTcpClient(System.err, System.in, System.out, optKey);
            try {
                container.connectToServer(endpoint, ClientEndpointConfig.Builder.create().build(), uri);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null)
                    msg = e.getClass().getName();
                throw new FatalError("could not connect to endpoint: " + msg);
            }
            try {
                endpoint.waitUntilDone();
            } catch (InterruptedException e) {
                throw new AssertionError("Unexpectedly interrupted");
            }
            System.exit(endpoint.getErrorCount());
        } catch (FatalError e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}

