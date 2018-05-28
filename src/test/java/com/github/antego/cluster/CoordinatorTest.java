package com.github.antego.cluster;

import com.github.antego.ConfigurationKey;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.antego.ConfigurationKey.ZOOKEEPER_ROOT_NODE_NAME;
import static com.github.antego.TestHelper.createPath;
import static com.github.antego.TestHelper.generateRandomNode;
import static com.github.antego.Utils.createZookeeperClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CoordinatorTest {
    private static Config config = ConfigFactory.load();
    private static GenericContainer zookeeperContainer;
    private static int zookeeperPort;
    private static ZooKeeper zookeeperClient;
    private ZooKeeper zookeeperForCoordinator;
    private ClusterWatcherFactory factory = mock(ClusterWatcherFactory.class);

    @BeforeClass
    public static void createTestVerifyClient() throws IOException {
        zookeeperContainer = new GenericContainer("bitnami/zookeeper:3.4.12")
                .withExposedPorts(2181)
                .withEnv("ALLOW_ANONYMOUS_LOGIN", "yes");
        zookeeperContainer.start();
        zookeeperPort = zookeeperContainer.getMappedPort(2181);
        zookeeperClient = createZookeeperClient(config.withValue(ConfigurationKey.ZOOKEEPER_PORT,
                ConfigValueFactory.fromAnyRef(zookeeperPort)));
    }

    @AfterClass
    public static void disconnectFromZookeeper() throws InterruptedException {
        zookeeperClient.close();
        zookeeperContainer.stop();
    }

    @Before
    public void testInNewRootNode() throws IOException {
        zookeeperForCoordinator = createZookeeperClient(config.withValue(ConfigurationKey.ZOOKEEPER_PORT,
                ConfigValueFactory.fromAnyRef(zookeeperPort)));
        config = config.withValue(ZOOKEEPER_ROOT_NODE_NAME,
                ConfigValueFactory.fromAnyRef("/" + UUID.randomUUID().toString()));
    }

    @Test
    public void shouldCreateRootNodeOnStart() throws Exception {
        Coordinator coordinator = new Coordinator(zookeeperForCoordinator, config, factory);

        coordinator.init();

        Stat stat = zookeeperClient.exists(config.getString(ZOOKEEPER_ROOT_NODE_NAME), false);
        assertTrue(stat != null);
    }

    @Test
    public void shouldDeleteNodeOnExit() throws Exception {
        Coordinator coordinator = new Coordinator(zookeeperForCoordinator, config, factory);

        coordinator.init();
        coordinator.advertiseSelf("1");
        coordinator.close();

        Stat stat = zookeeperClient.exists(config.getString(ZOOKEEPER_ROOT_NODE_NAME) + "/1", false);
        assertTrue(stat == null);
    }

    @Test
    public void shouldAssignWatcherOnInit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1); // need to wait till event comes back

        Coordinator coordinator = new Coordinator(zookeeperForCoordinator, config, new ClusterWatcherFactory() {
            @Override
            public ClusterWatcher createWatcher(Coordinator coordinator) {
                return spy(new ClusterWatcher(coordinator) {
                    @Override
                    public void process(WatchedEvent event) {
                        latch.countDown();
                    }
                });
            }
        });
        coordinator.init();

        createPath(zookeeperClient, generateRandomNode(config.getString(ZOOKEEPER_ROOT_NODE_NAME)));
        latch.await(10, TimeUnit.SECONDS);
        assertEquals(0L, latch.getCount());
    }

    @Test
    public void shouldRemoveSelf() throws Exception {
        Coordinator coordinator = new Coordinator(zookeeperForCoordinator, config, factory);

        coordinator.init();
        coordinator.advertiseSelf("1");
        coordinator.removeSelf();

        Stat stat = zookeeperClient.exists(config.getString(ZOOKEEPER_ROOT_NODE_NAME) + "/node1", false);
        assertTrue(stat == null);
    }

    @Test
    public void shouldCreateSelfNode() throws Exception {
        Coordinator coordinator = new Coordinator(zookeeperForCoordinator, config, factory);

        coordinator.init();
        coordinator.advertiseSelf("1");

        Stat stat = zookeeperClient.exists(config.getString(ZOOKEEPER_ROOT_NODE_NAME) + "/node1", false);
        assertTrue(stat != null);
    }
}
