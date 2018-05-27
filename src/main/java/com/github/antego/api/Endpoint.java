package com.github.antego.api;

import com.github.antego.ConfigurationKey;
import com.github.antego.storage.RouterStorage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.server.Server;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

public class Endpoint {
    private static final Config config = ConfigFactory.load();
    private final RouterStorage routerStorage;
    private CountDownLatch shutdown;
    private Server server;


    //todo secure
    public Endpoint(RouterStorage routerStorage, CountDownLatch shutdown) {
        this.routerStorage = routerStorage;
        this.shutdown = shutdown;
    }

    public void start() throws Exception {
        URI baseUri = UriBuilder.fromUri("http://")
                .host(config.getString(ConfigurationKey.JETTY_HOST))
                .port(config.getInt(ConfigurationKey.JETTY_PORT)).build();
        ResourceConfig config = new ResourceConfig(MetricResource.class).register(new StorageBinder());
        server = JettyHttpContainerFactory.createServer(baseUri, config);
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
        server.destroy();
    }

    public class StorageBinder extends AbstractBinder {
        @Override
        public void configure() {
            bind(routerStorage).to(RouterStorage.class);
            bind(shutdown).to(CountDownLatch.class);
        }
    }
}
