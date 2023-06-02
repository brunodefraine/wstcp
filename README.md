# wstcp

This is yet another implementation of the common idea to tunnel a TCP
connection over a WebSocket connection, this time written in Java. It can be
used to connect to an internet service from an environment that only allows web
traffic.

The implementation consists of a server, a J2EE webapp, that publishes
WebSocket endpoints that each correspond to a TCP destination. When the client,
a Java command-line program, connects to an endpoint, the TCP connection is
established by the server, and the traffic is forwarded over the WebSocket
connection to the client (on its stdin and stdout channels). 

The client can be used as a _proxy command_, or can be bound to a local port in
an inetd-style daemon.

## Building

The code can be built using the included `Makefile` after creating a
`Makefile.config` (from the corresponding `.templ` file). This file configures
the paths of the J2EE Servlet and WebSocket API JARs, and the JAR files of the
WebSocket client library.

Running `make` will create `wstcp-server.jar` and `wstcp-client.jar`.

## Usage

### Server

The `server` directory can be deployed as a J2EE webapp after creating a
`server/WEB-INF/server.conf` (from the corresponding `.templ` file). This file
configures the endpoints and the corresponding TCP destinations. The web
address of the configured endpoint is obtained by combining all the parts, for
example, if the endpoint `/mail` is configured, and the webapp is deployed with
context path `/wstcp` in a container that runs at `http://localhost:8080`, the
endpoint will have the following web address:

```
ws://localhost:8080/wstcp/mail
```

If the container is behind a web front, it should be configured to forward the
WebSocket connection. This is an example configuration for an Apache front, it
requires module `mod_proxy_wstunnel`:

```
ProxyPassMatch ^/wstcp/(mail|other_endpoint)$ ws://localhost:8080/wstcp/$1
```

The webapp will serve at its root a simple browser based client, written in HTML
and JavaScript, intended for testing purposes only. Here, you can enter the
address of an endpoint and establish a text-based connection.

### Client

The client can be invoked as:

```
java -jar client/wstcp-client.jar [-scramble] <endpoint-url>
```

When the `-scramble` option is used, the traffic is additionally scrambled to
make it appear as random data for packet inspection (and when the same data is
repeated on the same connection or on repeated connections, it will appear
different every time). Note that this is __not__ intended as a security
feature, someone with knowledge of the implementation can easily decrypt the
traffic. You should still use a secure protocol (such as SSL or SSH) over the
forwarded connection, or use a secure WebSocket connection (i.e. URL starting
with `wss://`).
