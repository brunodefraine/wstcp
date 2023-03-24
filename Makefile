EMPTY=
SPACE=$(EMPTY) $(EMPTY)
java-cp=$(subst $(SPACE),:,$(strip $(1)))

include Makefile.config

COLLECT=./collect.sh
JAVAC=javac
JAVACFLAGS=-Xlint:all
JAVACPFLAG=$(if $(call java-cp,$(CP)),-classpath $(call java-cp,$(CP)))

wstcp-src= \
  src/net/defraine/wstcp/Scrambler.java

wstcp-server-src= \
  src/net/defraine/wstcp/server/WSTcpServletContextListener.java \
  src/net/defraine/wstcp/server/WSTcpEndpoint.java

wstcp-client-src= \
  src/net/defraine/wstcp/client/WSTcpClient.java

.PHONY: all

all: client/wstcp-client.jar server/WEB-INF/lib/wstcp-server.jar

build/wstcp: $(wstcp-src) | build
	$(COLLECT) $@ $(JAVAC) $(JAVACFLAGS) $(JAVACPFLAG) -d build $(wstcp-src)

build/wstcp-server: CP = build $(SERVLETAPI) $(WEBSOCKETAPI)
build/wstcp-server: $(wstcp-server-src) build/wstcp
	$(COLLECT) $@ $(JAVAC) $(JAVACFLAGS) $(JAVACPFLAG) -d build $(wstcp-server-src)

build/wstcp-client: CP = build $(WEBSOCKETAPI)
build/wstcp-client: $(wstcp-client-src) build/wstcp
	$(COLLECT) $@ $(JAVAC) $(JAVACFLAGS) $(JAVACPFLAG) -d build $(wstcp-client-src)

client-manifest.txt: Makefile.config
	mkdir -p client
	cp $(CLIENTLIB) client
	echo 'Main-Class: net.defraine.wstcp.client.WSTcpClient' >$@
	echo 'Class-Path: $(notdir $(CLIENTLIB))' >>$@

client/wstcp-client.jar: client-manifest.txt build/wstcp build/wstcp-client
	cd build && jar cfm $(abspath $@) $(abspath client-manifest.txt) $(addprefix @,$(abspath build/wstcp build/wstcp-client))

server/WEB-INF/lib/wstcp-server.jar: build/wstcp build/wstcp-server
	mkdir -p server/WEB-INF/lib
	cd build && jar cf $(abspath $@) $(addprefix @,$(abspath $^))

build:
	mkdir -p build

