package com.github.antego.api;

import com.github.antego.db.Storage;
import org.eclipse.jetty.server.Server;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

public class Endpoint {
    private Server server;
    private Storage storage;

    //todo secure
    public Endpoint(Storage storage) {
        this.storage = storage;
    }

    public void start() throws Exception {
        URI baseUri = UriBuilder.fromUri("http://localhost/").port(9998).build();
        ResourceConfig config = new ResourceConfig(MetricResource.class).register(new StorageBinder());
        server = JettyHttpContainerFactory.createServer(baseUri, config);
        server.start();
    }

    public class StorageBinder extends AbstractBinder {
        @Override
        public void configure() {
            bind(storage).to(Storage.class);
        }
    }
}
