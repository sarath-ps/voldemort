package voldemort.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class ClientTrafficGenerator {

    String bootstrapURL;
    Collection<String> storeNames;
    Collection<Integer> zones;
    int threads;
    static Logger logger = Logger.getLogger(ClientTrafficGenerator.class);

    List<ClientTrafficVerifier> verifiers = new ArrayList<ClientTrafficVerifier>();

    public ClientTrafficGenerator(String bootstrapURL,
                                  Collection<String> storeNames,
                                  Collection<Integer> zones,
                                  int threads) {
        this.bootstrapURL = bootstrapURL;
        this.storeNames = storeNames;
        this.threads = threads;
        this.zones = zones;

        for(Integer zone: zones) {
            for(String storeName: storeNames) {
                for(int thread = 0; thread < threads; thread++) {
                    String clientName = storeName + "_Zone_" + zone + "_Thread_" + thread;
                    ClientTrafficVerifier verifier = new ClientTrafficVerifier(clientName,
                                                                               bootstrapURL,
                                                                               storeName,
                                                                               zone.intValue());
                    verifiers.add(verifier);
                }
            }
        }
    }

    public void start() {
        logger.info("-------------------------------");
        logger.info("       STARTING CLIENT         ");
        logger.info("-------------------------------");
        for(ClientTrafficVerifier verifier: verifiers) {
            verifier.initialize();
            verifier.start();
        }
        logger.info("-------------------------------");
        logger.info("        CLIENT STARTED         ");
        logger.info("-------------------------------");
    }

    public void stop() {
        logger.info("-------------------------------");
        logger.info("         STOPPING CLIENT       ");
        logger.info("-------------------------------");

        for(ClientTrafficVerifier verifier: verifiers) {
            verifier.stop();
        }

        logger.info("-------------------------------");
        logger.info("         STOPPED CLIENT        ");
        logger.info("-------------------------------");

    }

    public void verifyIfClientsDetectedNewClusterXMLs() {
        // verify that all clients has new cluster now
        Integer failCount = 0;
        for(ClientTrafficVerifier verifier: verifiers) {
            if(verifier.client instanceof LazyStoreClient) {
                LazyStoreClient<String, String> lsc = (LazyStoreClient<String, String>) verifier.client;
                if(lsc.getStoreClient() instanceof ZenStoreClient) {
                    ZenStoreClient<String, String> zsc = (ZenStoreClient<String, String>) lsc.getStoreClient();
                    Long clusterMetadataVersion = zsc.getAsyncMetadataVersionManager()
                                                     .getClusterMetadataVersion();
                    if(clusterMetadataVersion == 0) {
                        failCount++;
                        logger.error(String.format("The client %s did not pick up the new cluster metadata\n",
                                                   verifier.clientName));
                    }
                } else {
                    throw new RuntimeException("There is problem with DummyClient's real client's real client, which should be ZenStoreClient but not");
                }
            } else {
                throw new RuntimeException("There is problem with DummyClient's real client which should be LazyStoreClient but not");
            }
        }
        if(failCount > 0) {
            throw new RuntimeException(failCount.toString()
                                       + " client(s) did not pickup new metadata");
        }
    }

    public void verifyPostConditions() {
        for(ClientTrafficVerifier client: verifiers) {
            if(!client.stopped) {
                client.stop();
            }
        }

        for(ClientTrafficVerifier client: verifiers) {
            Map<String, Integer> eMap = client.exceptionCount;
            logger.info("-------------------------------------------------------------------");
            logger.info("Client Operation Info of [" + client.clientName + "]");
            logger.info(client.requestCount.toString());
            if(eMap.size() == 0) {
                logger.info("No Exception reported by ClientTrafficVerifier(ObsoleteVersionException are ignored)");

                logger.info("-------------------------------------------------------------------");
            } else {
                logger.info("Exceptions Count Map of the client: ");
                logger.info(eMap.toString());
                logger.info("-------------------------------------------------------------------");
                throw new RuntimeException("Found Exceptions by Client" + eMap);
            }
        }
    }

}
