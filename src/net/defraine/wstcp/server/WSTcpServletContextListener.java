package net.defraine.wstcp.server;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

public class WSTcpServletContextListener implements ServletContextListener {
    protected ServletContext context;

    protected void deployWSTcpEndpoint(ServerContainer sc, String path, String host, int port) {
        ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(WSTcpEndpoint.class, path)
            .configurator(new ServerEndpointConfig.Configurator() {
                @Override
                public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                    assert endpointClass.equals(WSTcpEndpoint.class);
                    WSTcpEndpoint endpoint = new WSTcpEndpoint(context, host, port);
                    return endpointClass.cast(endpoint);
                }
            })
            .build();
        try {
            sc.addEndpoint(sec);
            context.log("registered endpoint at " + context.getContextPath() + path);
        } catch (DeploymentException de) {
            context.log("failed to deploy endpoint", de);
        }
    }

    protected static final String configFile = "/WEB-INF/server.conf";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        context = sce.getServletContext();
        ServerContainer sc = (ServerContainer)context.getAttribute(ServerContainer.class.getName());
        InputStream is = context.getResourceAsStream(configFile);
        if (is == null) {
            context.log("Could not access config file at " + configFile);
            return;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        try {
            String line;
            int lineNr = 0;
            while ((line = in.readLine()) != null) {
                ++lineNr;
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#')
                    continue;
                String[] words = line.split("\\s+", -1);
                if (words.length != 2) {
                    context.log("parse error in config file line " + lineNr + ": need 2 items");
                    continue;
                }
                String path = words[0];
                int pos = words[1].indexOf(":");
                if (pos == -1) {
                    context.log("parse error in config file line " + lineNr + ": no port");
                    continue;
                }
                if (pos == 0) {
                    context.log("parse error in config file line " + lineNr + ": no host");
                    continue;
                }
                String host = words[1].substring(0, pos);
                int port;
                try {
                    port = Integer.parseInt(words[1].substring(pos+1));
                } catch (NumberFormatException e) {
                    context.log("parse error in config file line " + lineNr + ": invalid port");
                    continue;
                }
                if (path.charAt(0) != '/') {
                    context.log("invalid entry in config file line " + lineNr + ": path must begin with /");
                    continue;
                }
                deployWSTcpEndpoint(sc, path, host, port);
            }
        } catch (IOException e) {
            context.log("IO error reading config file", e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                context.log("IO error closing config file", e);
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) { }
}
