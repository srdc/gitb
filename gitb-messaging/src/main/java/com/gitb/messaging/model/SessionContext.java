package com.gitb.messaging.model;

import com.gitb.core.ActorConfiguration;
import com.gitb.core.Configuration;
import com.gitb.core.ErrorCode;
import com.gitb.exceptions.GITBEngineInternalError;
import com.gitb.messaging.ServerUtils;
import com.gitb.messaging.layer.AbstractMessagingHandler;
import com.gitb.messaging.server.IMessagingServer;
import com.gitb.messaging.server.IMessagingServerWorker;
import com.gitb.utils.ActorUtils;
import com.gitb.utils.ConfigurationUtils;
import com.gitb.utils.ErrorUtils;
import com.gitb.utils.map.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by serbay on 9/24/14.
 * <p/>
 * Class that encapsulates all the related information for a messaging session
 */
public class SessionContext {

    private static final Logger logger = LoggerFactory.getLogger(SessionContext.class);

    private final String sessionId;
    private final AbstractMessagingHandler messagingHandler;
    private final IMessagingServer messagingServer;
    private final List<ActorConfiguration> actorConfigurations;
    private final List<ActorConfiguration> serverActorConfigurations;
    private final Map<Tuple<String>, IMessagingServerWorker> workers;
    private final Map<String, List<TransactionContext>> transactionMappings;

    public SessionContext(String sessionId, AbstractMessagingHandler messagingHandler, List<ActorConfiguration> actorConfigurations, IMessagingServer messagingServer) {
        this.sessionId = sessionId;
        this.messagingHandler = messagingHandler;
        this.actorConfigurations = actorConfigurations;
        this.messagingServer = messagingServer;
        this.serverActorConfigurations = new CopyOnWriteArrayList<>();
        this.workers = new ConcurrentHashMap<>();
        this.transactionMappings = new ConcurrentHashMap<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<ActorConfiguration> getActorConfigurations() {
        return actorConfigurations;
    }

    public List<ActorConfiguration> getServerActorConfigurations() {
        return serverActorConfigurations;
    }

    public IMessagingServerWorker getWorker(String actor, String endpoint) {
        return workers.get(new Tuple<>(new String[]{actor, endpoint}));
    }

    public void setWorker(String actor, String endpoint, IMessagingServerWorker worker) {
        workers.put(new Tuple<>(new String[]{actor, endpoint}), worker);
    }

    public void removeWorker(String actor, String endpoint) {
        Tuple<String> actorEndpointTuple = new Tuple<>(new String[]{actor, endpoint});
        if (workers.containsKey(actorEndpointTuple)) {
            IMessagingServerWorker worker = workers.remove(actorEndpointTuple);
            if (worker.getNetworkingSessionManager().getNumberOfActiveSessions() == 0
                    && worker.isActive()) {
                messagingServer.close(worker.getPort());
            }
        }
    }

    public void end() {
        for (List<TransactionContext> transactions : transactionMappings.values()) {
            for (TransactionContext transactionContext : transactions) {
                transactionContext.end();
            }
        }

        transactionMappings.clear();
    }

    /**
     * Ends the transactions with the transaction id
     * @param transactionId Transaction id
     * @return Closed transactions
     */
    public List<TransactionContext> endTransaction(String transactionId) {
        List<TransactionContext> transactions = transactionMappings.remove(transactionId);

        for(TransactionContext transactionContext : transactions) {
            transactionContext.end();
        }


        return transactions;
    }

    /**
     * Begin a new transaction with the actors "from" and "to"
     * @param transactionId Transaction id
     * @param from From actor identifier
     * @param to To actor identifier
     * @param configurations Configurations
     * @return Transactions initialized with the given actors. This may be more than one if both actors are SUTs.
     */
    public List<TransactionContext> beginTransaction(String transactionId, String from, String to, List<Configuration> configurations) {
        if (!transactionMappings.containsKey(transactionId)) {
            String fromActorId = ActorUtils.extractActorId(from);
            String fromEndpointName = ActorUtils.extractEndpointName(from);

            String toActorId = ActorUtils.extractActorId(to);
            String toEndpointName = ActorUtils.extractEndpointName(to);

            List<TransactionContext> transactions  = new ArrayList<>();

            for (ActorConfiguration actorConfiguration : actorConfigurations) {
                ActorConfiguration serverActorConfiguration = null;
                TransactionContext transactionContext = null;
                if ((fromEndpointName == null && actorConfiguration.getActor().equals(from))
                        || (fromEndpointName != null && actorConfiguration.getActor().equals(fromActorId) && actorConfiguration.getEndpoint().equals(fromEndpointName))) {

                    serverActorConfiguration = ActorUtils.getActorConfiguration(serverActorConfigurations, toActorId, toEndpointName);

                    transactionContext = new TransactionContext(transactionId, serverActorConfiguration, actorConfiguration, configurations);

                } else if((toEndpointName == null && actorConfiguration.getActor().equals(to))
                        || (toEndpointName != null && actorConfiguration.getActor().equals(toActorId) && actorConfiguration.getEndpoint().equals(toEndpointName))) {
                    serverActorConfiguration = ActorUtils.getActorConfiguration(serverActorConfigurations, fromActorId, fromEndpointName);

                    transactionContext = new TransactionContext(transactionId, serverActorConfiguration, actorConfiguration, configurations);
                }

                if(transactionContext != null) {
                    transactions.add(transactionContext);
                }
            }

            if(transactions.size() > 0) {
                transactionMappings.put(transactionId, transactions);

                return transactions;
            } else {
                throw new GITBEngineInternalError(ErrorUtils.errorInfo(ErrorCode.INVALID_TEST_CASE, "None of the transactions could be initialized"));
            }
        } else {
            return transactionMappings.get(transactionId);
        }
    }

    /**
     * Get transactions with transaction id
     * @param transactionId Transaction id
     * @return Transactions that has the given transaction id. This may be more than one if both actors are SUTs.
     */
    public List<TransactionContext> getTransactions(String transactionId) {
        return transactionMappings.get(transactionId);
    }


    public TransactionContext getTransaction(String transactionId, String actor) {
        List<TransactionContext> transactions = transactionMappings.get(transactionId);

        String actorId = ActorUtils.extractActorId(actor);
        String endpointName = ActorUtils.extractEndpointName(actor);

        for(TransactionContext transaction : transactions) {
            ActorConfiguration with = transaction.getWith();

            if((endpointName == null && actorId.equals(with.getActor()))
                    || (endpointName != null && endpointName.equals(with.getEndpoint()) && actorId.equals(with.getActor()))) {
                return transaction;
            }
        }

        return null;
    }

    /**
     * Get the transaction with the given ip address
     * @param address IP address
     * @return Transaction with the actor that has the given ip address.
     * @throws UnknownHostException
     */
    public TransactionContext getTransaction(InetAddress address, int incomingPort) {
        TransactionContext lastAwaitingTransaction = null;
        try {
            for (List<TransactionContext> transactions : transactionMappings.values()) {
                for(TransactionContext transactionContext : transactions) {
                    Configuration ipAddressConfig = ConfigurationUtils.getConfiguration(transactionContext.getWith().getConfig(), ServerUtils.IP_ADDRESS_CONFIG_NAME);

                    InetAddress actorAddress = null;
                        actorAddress = InetAddress.getByName(ipAddressConfig.getValue());
                    int serverPort = -1;

                    if(transactionContext.getSelf() != null) {
                        Configuration serverPortConfig = ConfigurationUtils.getConfiguration(transactionContext.getSelf().getConfig(), ServerUtils.PORT_CONFIG_NAME);

                        if(serverPortConfig == null) {
                            continue;
                        }

                        serverPort = Integer.parseInt(serverPortConfig.getValue());
                    }


                    if (actorAddress.equals(address) && (incomingPort == serverPort || serverPort == -1)) {
                        if (lastAwaitingTransaction == null) {
                            lastAwaitingTransaction = transactionContext;
                        } else {
                            if (transactionContext.getStartTime() > lastAwaitingTransaction.getStartTime()) {
                                lastAwaitingTransaction = transactionContext;
                            }
                        }
                    }
                }
            }
        } catch (UnknownHostException e) {
            logger.error("An error occurred while trying to find the transaction coming from [" + address + "] and binded to ["+incomingPort+"] port.", e);
        }

        return lastAwaitingTransaction;
    }

    public Collection<TransactionContext> getTransactions() {
        List<TransactionContext> result = new ArrayList<>();

        for(List<TransactionContext> transactions : transactionMappings.values()) {
            for(TransactionContext transaction : transactions) {
                result.add(transaction);
            }
        }

        return result;
    }

    public AbstractMessagingHandler getMessagingHandler() {
        return messagingHandler;
    }
}
