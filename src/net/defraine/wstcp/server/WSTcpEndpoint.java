package net.defraine.wstcp.server;

import net.defraine.wstcp.Scrambler;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import javax.servlet.ServletContext;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

public class WSTcpEndpoint extends Endpoint {
    protected final ServletContext context;
    protected final String host;
    protected final int port;

    protected Session session;
    protected Socket sock;
    protected Thread reader;

    public WSTcpEndpoint(ServletContext context, String host, int port) {
        this.context = context;
        this.host = host;
        this.port = port;
    }

    protected void log(String message) {
        context.log("session " + session.getId() + ": " + message);
    }

    protected void closeFatal(String reason) {
        if (session.isOpen()) {
            log("closing for fatal: " + reason);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, reason));
                // note: onClose will disposeSocket
            } catch (IOException e) {
                context.log("IO error on websocket", e);
            }
        } else {
            log("fatal: " + reason);
            disposeSocket();
        }
    }

    protected void disposeSocket() {
        if (sock.isConnected() && !sock.isClosed()) {
            log("closing connection");
            try {
                sock.close();
            } catch (IOException e) {
                log("IO error while closing: " + e.getMessage());
            }
        }
    }

    @Override
    public void onOpen(Session s, EndpointConfig ec) {
        session = s;
        final Scrambler sendScrambler;
        final Scrambler recvScrambler;
        List<String> keyParams = s.getRequestParameterMap().get("key");
        if (keyParams != null && keyParams.size() == 1) {
            String keyParam = keyParams.iterator().next();
            log("got key: " + keyParam);
            long key;
            try {
                key = Long.parseUnsignedLong(keyParam, 16);
            } catch (NumberFormatException nfe) {
                log("closing connection due to invalid key");
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "invalid key"));
                } catch (IOException e) {
                    context.log("IO error on websocket", e);
                }
                return;
            }
            sendScrambler = new Scrambler(~key);
            recvScrambler = new Scrambler(key);
        } else {
            sendScrambler = null;
            recvScrambler = null;
        }
        final InputStream is;
        final OutputStream os;
        try {
            sock = new Socket(host, port);
            is = sock.getInputStream();
            os = sock.getOutputStream();
        } catch (UnknownHostException e) {
            closeFatal("Could not resolve");
            return;
        } catch (IOException e) {
            closeFatal("Could not connect: " + e.getMessage());
            return;
        }
        log("connected to " + host + ":" + port);
        CountDownLatch toFinish = new CountDownLatch(1);

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
                            sock.shutdownOutput();
                            toFinish.countDown();
                        }
                    }
                } catch (IOException e) {
                    closeFatal("IO error writing to socket: " + e.getMessage());
                }
            }
        });
        reader = new Thread() {
            private static final int bufferSize = 4*1024;

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
                            closeFatal("IO error reading from socket: " + e.getMessage());
                            return;
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
                    try {
                        toFinish.await();
                    } catch (InterruptedException e) {
                        // see onClose: already closed for abnormal reason
                        return;
                    }
                    log("closing connection");
                    try {
                        sock.close();
                    } catch (IOException e) {
                        closeFatal("IO error closing socket: " + e.getMessage());
                        return;
                    }
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Bye"));
                } catch (IOException e) {
                    context.log("IO error on websocket", e);
                } finally {
                    log("exiting reader thread");
                }
            }
        };
        reader.start();
    }

    @Override
    public void onClose(Session s, CloseReason c) {
        if (c.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE) {
            log("Websocket closed: " + c);
            disposeSocket();
            if (reader != null) reader.interrupt();
        }
    }

}
