package com.gitb.engine.testcase;

import com.gitb.ModuleManager;
import com.gitb.core.*;
import com.gitb.engine.messaging.MessagingContext;
import com.gitb.exceptions.GITBEngineInternalError;
import com.gitb.messaging.IMessagingHandler;
import com.gitb.messaging.model.InitiateResponse;
import com.gitb.repository.ITestCaseRepository;
import com.gitb.tbs.SUTConfiguration;
import com.gitb.tdl.*;
import com.gitb.types.DataType;
import com.gitb.types.DataTypeFactory;
import com.gitb.types.MapType;
import com.gitb.types.StringType;
import com.gitb.utils.ActorUtils;
import com.gitb.utils.ErrorUtils;
import com.gitb.utils.map.Tuple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by serbay on 9/3/14.
 *
 * Class that encapsulates all the necessary information for a test case execution session
 */
public class TestCaseContext {
    /**
     * Test case to be executed
     */
    private final TestCase testCase;

    /**
     * Test session id given to the testbed service client to control the execution
     */
    private final String sessionId;

    /**
     * Test case scope where all the variables, artifacts are stored and accessed.
     */
    private TestCaseScope scope;

    /**
     * Configurations of SUTs which are provided by the users. Map key is set to (actorId, endpointName) tuples.
     * Note that in the case of an actor having just one endpoint, an empty endpoint value is possible. In this
     * case, the key part will be in the (actorId, null) form.
     */
    private Map<Tuple<String>, ActorConfiguration> sutConfigurations;

    /**
     * MessagingContext for each handler used in the TestCase
     */
    private final Map<String, MessagingContext> messagingContexts;

    /**
     * Roles defined in the TestCase
     */
    private final Map<String, TestRole> actorRoles;

    /**
     * Corresponding simulated actor configurations for each SUT (key: actor name & actor endpoint of SUT)
     * Map key is set to (actorId, endpointName) tuples whereas value is set to simulated actor configurations
     * for the (actorId, endpointName) tuple. Note that in the case of an actor having just one endpoint,
     * the key part will be in the (actorId, null) form.
     */
    private final Map<Tuple<String>, List<ActorConfiguration>> sutHandlerConfigurations;

    /**
     * Current state of the test case execution
     */
    private TestCaseStateEnum currentState;

	/**
	 * Test session state enumeration
	 */
    public static enum TestCaseStateEnum {
		/**
		 * Just initialized, waiting for configuration
		 */
        IDLE,
		/**
		 * Configured, waiting for preliminary initiation
		 */
        CONFIGURATION,
		/**
		 * Preliminary requirements are sent to SUTs, waiting for inputs
		 */
        PRELIMINARY,
		/**
		 * Preliminary phase completed, waiting for execution
		 */
        READY,
		/**
		 * Execution is in progress
		 */
        EXECUTION,
		/**
		 * Execution is completed or stopped by user or exit with some error
		 */
        STOPPED
    }

    public TestCaseContext(TestCase testCase, String sessionId) {
        this.currentState = TestCaseStateEnum.IDLE;
        this.testCase = testCase;
        this.sessionId = sessionId;
        this.sutConfigurations = new ConcurrentHashMap<>();
        this.sutHandlerConfigurations = new ConcurrentHashMap<>();
        this.messagingContexts = new ConcurrentHashMap<>();
        this.scope = new TestCaseScope(this);

        processVariables();

        actorRoles = new HashMap<>();
        // Initialize configuration lists for SutHandlerConfigurations
        for(TestRole role : this.testCase.getActors().getActor()) {
            actorRoles.put(role.getId(), role);
        }
    }

    private void processVariables() {
        //process variables to create test case scope symbols
        if (this.testCase.getVariables() != null) {
            for (Variable variable : this.testCase.getVariables().getVar()) {
                DataType value;
                try {
                    value = DataTypeFactory.getInstance().create(variable);
                } catch (Exception e) {
                    value = DataTypeFactory.getInstance().create(variable.getType());
                }
                this.scope.createVariable(variable.getName()).setValue(value);

            }
        }
    }

    /**
     * Configure simulated actors for the given SUT configurations
     * @param configurations SUT configurations
     * @return Simulated actor configurations for each (actorId, endpointName) tuple
     */
    public List<SUTConfiguration> configure(List<ActorConfiguration> configurations){

	    for(ActorConfiguration actorConfiguration : configurations) {
		    Tuple<String> actorIdEndpointTupleKey = new Tuple<>(new String[] {actorConfiguration.getActor(), actorConfiguration.getEndpoint()});
		    sutConfigurations.put(actorIdEndpointTupleKey, actorConfiguration);
		    sutHandlerConfigurations.put(actorIdEndpointTupleKey, new CopyOnWriteArrayList<ActorConfiguration>());
	    }

	    List<SUTConfiguration> sutConfigurations = configureSimulatedActorsForSUTs(configurations);

	    // set the configuration parameters given in the test case definition if they are not set already
	    for(TestRole role : testCase.getActors().getActor()) {
		    for(Endpoint endpoint : role.getEndpoint()) {
			    for(Parameter parameter : endpoint.getConfig()) {
				    setSUTConfigurationParameter(sutConfigurations, role.getId(), endpoint.getName(), parameter);
			    }
		    }
	    }

	    bindActorConfigurationsToScope();

	    return sutConfigurations;
    }

	private void setSUTConfigurationParameter(List<SUTConfiguration> sutConfigurations, String id, String endpoint, Parameter parameter) {
		for(SUTConfiguration sutConfiguration : sutConfigurations) {
			for(ActorConfiguration actorConfiguration : sutConfiguration.getConfigs()) {
				if(id.equals(actorConfiguration.getActor()) &&
                        ((endpoint != null && actorConfiguration.getEndpoint() != null && endpoint.equals(actorConfiguration.getEndpoint()))
                        || actorConfiguration.getEndpoint() == null)) {

					boolean configurationExists = false;
					for(Configuration configuration : actorConfiguration.getConfig()) {
						if(configuration.getName().equals(parameter.getName())) {
							configurationExists = true;
							break;
						}
					}

					if(!configurationExists) {
						Configuration configuration = new Configuration();
						configuration.setName(parameter.getName());
						configuration.setValue(parameter.getValue());

						actorConfiguration.getConfig().add(configuration);
					}

					break;
				}
			}
		}
	}

	private void bindActorConfigurationsToScope() {

		DataTypeFactory factory = DataTypeFactory.getInstance();

		for(ActorConfiguration actorConfiguration : sutConfigurations.values()) {
			TestCaseScope.ScopedVariable variable = scope.createVariable(actorConfiguration.getActor());

			MapType map = (MapType) factory.create(DataType.MAP_DATA_TYPE);

			for(Configuration configuration : actorConfiguration.getConfig()) {
				StringType configurationValue = (StringType) factory.create(DataType.STRING_DATA_TYPE);
				configurationValue.setValue(configuration.getValue());

				map.addItem(configuration.getName(), configurationValue);
			}

			List<ActorConfiguration> actorSUTConfigurations = sutHandlerConfigurations.get(new Tuple<>(new String[] {actorConfiguration.getActor(), actorConfiguration.getEndpoint()}));

			for(ActorConfiguration sutConfiguration : actorSUTConfigurations) {
				MapType sutConfigurationMap = (MapType) factory.create(DataType.MAP_DATA_TYPE);

				for(Configuration configuration : sutConfiguration.getConfig()) {
					StringType configurationValue = (StringType) factory.create(DataType.STRING_DATA_TYPE);
					configurationValue.setValue(configuration.getValue());

					sutConfigurationMap.addItem(configuration.getName(), configurationValue);
				}

				map.addItem(sutConfiguration.getActor(), sutConfigurationMap);
			}

			variable.setValue(map);
		}
	}

	private List<SUTConfiguration> configureSimulatedActorsForSUTs(List<ActorConfiguration> configurations) {
		List<TransactionInfo> testCaseTransactions = createTransactionInfo(testCase.getSteps());
		Map<String, MessagingContextBuilder> messagingContextBuilders = new HashMap<>();

        // collect all transactions needed to configure the messaging handlers
		for(TransactionInfo transactionInfo : testCaseTransactions) {
			TestRole fromRole = actorRoles.get(transactionInfo.fromActorId);
			TestRole toRole = actorRoles.get(transactionInfo.toActorId);

			if(!messagingContextBuilders.containsKey(transactionInfo.handler)) {
				messagingContextBuilders.put(transactionInfo.handler, new MessagingContextBuilder(transactionInfo.handler));
			}

			MessagingContextBuilder builder = messagingContextBuilders.get(transactionInfo.handler);
            builder.incrementTransactionCount();

			if(fromRole.getRole() == TestRoleEnumeration.SUT
				&& toRole.getRole() == TestRoleEnumeration.SUT) {
				// both of them are SUTs, messaging handler acts as a proxy between them
				builder.addActorConfiguration(transactionInfo.toActorId, transactionInfo.toEndpointName, ActorUtils.getActorConfiguration(configurations, transactionInfo.fromActorId, transactionInfo.fromEndpointName))
					   .addActorConfiguration(transactionInfo.fromActorId, transactionInfo.fromEndpointName, ActorUtils.getActorConfiguration(configurations, transactionInfo.toActorId, transactionInfo.toEndpointName));
			} else if(fromRole.getRole() == TestRoleEnumeration.SUT && toRole.getRole() == TestRoleEnumeration.SIMULATED) {
				// just one of them is SUT, messaging handler acts as the simulated actor
				builder.addActorConfiguration(transactionInfo.toActorId, transactionInfo.toEndpointName, ActorUtils.getActorConfiguration(configurations, transactionInfo.fromActorId, transactionInfo.fromEndpointName));
			} else if(fromRole.getRole() == TestRoleEnumeration.SIMULATED && toRole.getRole() == TestRoleEnumeration.SUT) {
				// just one of them is SUT, messaging handler acts as the simulated actor
				builder.addActorConfiguration(transactionInfo.fromActorId, transactionInfo.fromEndpointName, ActorUtils.getActorConfiguration(configurations, transactionInfo.toActorId, transactionInfo.toEndpointName));
			} else {
				// both of them are simulated actors, invalid test case
				throw new GITBEngineInternalError(ErrorUtils.errorInfo(ErrorCode.INVALID_TEST_CASE, "Both actors can not be simulated actors in a messaging transaction."));
			}
		}

		List<SUTConfiguration> handlerConfigurations = new ArrayList<>();

		for(MessagingContextBuilder builder : messagingContextBuilders.values()) {
			MessagingContext messagingContext = builder.build();
			handlerConfigurations.addAll(messagingContext.getSutHandlerConfigurations());

			messagingContexts.put(builder.getHandler(), messagingContext);
		}

        List<SUTConfiguration> configurationsBySUTActor = groupGeneratedSUTConfigurationsBySUTActor(handlerConfigurations);

        for(SUTConfiguration sutConfiguration : configurationsBySUTActor) {
            List<ActorConfiguration> actorConfigurations = sutHandlerConfigurations.get(new Tuple<>(new String[] {sutConfiguration.getActor(), sutConfiguration.getEndpoint()}));

            actorConfigurations.addAll(sutConfiguration.getConfigs());
        }

        return configurationsBySUTActor;

	}

	private List<SUTConfiguration> groupGeneratedSUTConfigurationsBySUTActor(List<SUTConfiguration> sutHandlerConfigurations) {
		Map<Tuple<String>, SUTConfiguration> groups = new HashMap<>();

		for(SUTConfiguration sutConfiguration : sutHandlerConfigurations) {
			Tuple<String> actorTuple = new Tuple<>(new String[]{sutConfiguration.getActor(), sutConfiguration.getEndpoint()});
			if(!groups.containsKey(actorTuple)) {
				SUTConfiguration groupedSUTConfiguration = new SUTConfiguration();
				groupedSUTConfiguration.setActor(actorTuple.getContents()[0]);
				groupedSUTConfiguration.setEndpoint(actorTuple.getContents()[1]);
				groups.put(actorTuple, groupedSUTConfiguration);
			}

			SUTConfiguration groupedSUTConfiguration = groups.get(actorTuple);
			groupedSUTConfiguration.getConfigs().addAll(sutConfiguration.getConfigs());
		}


		List<SUTConfiguration> groupedSUTConfigurations = new ArrayList<>();

		for(SUTConfiguration group : groups.values()) {
			groupedSUTConfigurations.add(group);
		}

		return groupedSUTConfigurations;
	}

    /**
     * Traverses test case steps to generate transaction information containing (from, handler, to) information
     * @param sequence test step sequence
     * @return transactions occurring in the given sequence
     */
    private List<TransactionInfo> createTransactionInfo(Sequence sequence) {
        List<TransactionInfo> transactions = new ArrayList<>();
        for(Object step : sequence.getSteps()) {
            if(step instanceof Sequence) {
                transactions.addAll(createTransactionInfo((Sequence) step));
            } else if(step instanceof BeginTransaction) {
                BeginTransaction beginTransactionStep = (BeginTransaction) step;

	            String fromActorId = ActorUtils.extractActorId(beginTransactionStep.getFrom());
	            String fromEndpoint = ActorUtils.extractEndpointName(beginTransactionStep.getFrom());

	            String toActorId = ActorUtils.extractActorId(beginTransactionStep.getTo());
	            String toEndpoint = ActorUtils.extractEndpointName(beginTransactionStep.getTo());

                transactions.add(new TransactionInfo(fromActorId, fromEndpoint, toActorId, toEndpoint, beginTransactionStep.getHandler()));
            } else if (step instanceof IfStep) {
	            transactions.addAll(createTransactionInfo(((IfStep) step).getThen()));
	            transactions.addAll(createTransactionInfo(((IfStep) step).getElse()));
            } else if(step instanceof WhileStep) {
	            transactions.addAll(createTransactionInfo(((WhileStep) step).getDo()));
            } else if(step instanceof ForEachStep) {
	            transactions.addAll(createTransactionInfo(((ForEachStep) step).getDo()));
            } else if(step instanceof RepeatUntilStep) {
	            transactions.addAll(createTransactionInfo(((RepeatUntilStep) step).getDo()));
            } else if(step instanceof FlowStep) {
	            for(Sequence thread : ((FlowStep) step).getThread()) {
		            transactions.addAll(createTransactionInfo(thread));
	            }
            } else if(step instanceof CallStep) {
	            // find scriptlet in the test case (if it is inline)
	            Scriptlet scriptlet = null;
	            for(Scriptlet s : testCase.getScriptlets().getScriptlet()) {
		            if(s.getId().equals(((CallStep) step).getPath())) {
			            scriptlet = s;
		            }
	            }

	            // find the scriptlet in repositories
	            ITestCaseRepository repository = ModuleManager.getInstance().getTestCaseRepository();
	            if(repository.isScriptletAvailable(((CallStep) step).getPath())) {
		            scriptlet = repository.getScriptlet(((CallStep) step).getPath());
	            }

	            if(scriptlet != null) {
		            transactions.addAll(createTransactionInfo(scriptlet.getSteps()));
	            }
            }
        }

        return transactions;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public TestCaseScope getScope() {
        return scope;
    }

    public void setScope(TestCaseScope scope) {
        this.scope = scope;
    }

    public String getSessionId() {
        return sessionId;
    }

    public MessagingContext getMessagingContext(String handler) {
        return messagingContexts.get(handler);
    }

	public TestCaseStateEnum getCurrentState() {
		return currentState;
	}

	public void setCurrentState(TestCaseStateEnum currentState) {
		this.currentState = currentState;
	}

    public void destroy(){
        destroyMessagingContexts();
    }

	private void destroyMessagingContexts() {
		for(MessagingContext messagingContext:messagingContexts.values()){
			messagingContext.getHandler().endSession(messagingContext.getSessionId());
		}
		messagingContexts.clear();
	}

	public TestArtifact getTestArtifact(String name) {
		if(testCase.getImports() != null) {
			for(Object o : testCase.getImports().getArtifactOrModule()) {
				if (o instanceof TestArtifact) {
					TestArtifact testArtifact = (TestArtifact) o;

					if(testArtifact.getName().equals(name)) {
						return testArtifact;
					}
				}
			}
		}

		return null;
	}

    public MessagingContext endMessagingContext(String handler) {
        return messagingContexts.remove(handler);
    }

    public Collection<MessagingContext> getMessagingContexts() {
        return messagingContexts.values();
    }

	private static class MessagingContextBuilder {
		private final String handler;
		private final Map<Tuple<String>, ActorConfiguration> sutConfigurations;
        private int transactionCount;

		public MessagingContextBuilder(String handler) {
			this.handler = handler;
			this.sutConfigurations = new HashMap<>();
            this.transactionCount = 0;
		}

        public void incrementTransactionCount() {
            this.transactionCount++;
        }

		public MessagingContextBuilder addActorConfiguration(String actorIdToBeSimulated, String endpointNameToBeSimulated,
		                                                     ActorConfiguration sutActorConfiguration) {
			Tuple<String> actorTuple = new Tuple<>(new String[]{
				actorIdToBeSimulated, endpointNameToBeSimulated,
				sutActorConfiguration.getActor(), sutActorConfiguration.getEndpoint()
			});

			if(!sutConfigurations.containsKey(actorTuple)) {
				sutConfigurations.put(actorTuple, sutActorConfiguration);
			}

			return this;
		}

		public MessagingContext build() {
			ModuleManager moduleManager = ModuleManager.getInstance();

			IMessagingHandler messagingHandler = moduleManager.getMessagingHandler(handler);

			List<ActorConfiguration> configurations = new ArrayList<>(sutConfigurations.values());

			InitiateResponse initiateResponse = messagingHandler.initiate(configurations);

			for(Map.Entry<Tuple<String>, ActorConfiguration> entry : sutConfigurations.entrySet()) {
				ActorConfiguration simulatedActor = ActorUtils.getActorConfiguration(initiateResponse.getActorConfigurations(), entry.getValue().getActor(), entry.getValue().getEndpoint());

				Tuple<String> actorTuple = entry.getKey();
				String actorIdToBeSimulated = actorTuple.getContents()[0];
				String endpointNameToBeSimulated = actorTuple.getContents()[1];

				simulatedActor.setActor(actorIdToBeSimulated);
				simulatedActor.setEndpoint(endpointNameToBeSimulated);
			}

			Map<Tuple<String>, SUTConfiguration> sutHandlerConfigurations = new HashMap<>();

			for(Map.Entry<Tuple<String>, ActorConfiguration> entry : sutConfigurations.entrySet()) {
				Tuple<String> concatenatedActorTuple = entry.getKey();

				String actorIdToBeSimulated = concatenatedActorTuple.getContents()[0];
				String endpointNameToBeSimulated = concatenatedActorTuple.getContents()[1];

				String sutActorId = concatenatedActorTuple.getContents()[2];
				String sutEndpointName = concatenatedActorTuple.getContents()[3];

				ActorConfiguration simulatedActor = ActorUtils.getActorConfiguration(initiateResponse.getActorConfigurations(), actorIdToBeSimulated, endpointNameToBeSimulated);

				Tuple<String> sutActorTuple = new Tuple<>(new String[] {sutActorId, sutEndpointName});

				if(!sutHandlerConfigurations.containsKey(sutActorTuple)) {
					SUTConfiguration sutHandlerConfiguration = new SUTConfiguration();
					sutHandlerConfiguration.setActor(sutActorId);
					sutHandlerConfiguration.setEndpoint(sutEndpointName);

					sutHandlerConfigurations.put(sutActorTuple, sutHandlerConfiguration);
				}

				SUTConfiguration sutHandlerConfiguration = sutHandlerConfigurations.get(sutActorTuple);

				sutHandlerConfiguration.getConfigs().add(simulatedActor);
			}

			return new MessagingContext(messagingHandler, initiateResponse.getSessionId(),
				initiateResponse.getActorConfigurations(), new ArrayList<>(sutHandlerConfigurations.values()), transactionCount);
		}

		public String getHandler() {
			return handler;
		}
	}

    private static class TransactionInfo {
        public final String fromActorId;
	    public final String fromEndpointName;

        public final String toActorId;
	    public final String toEndpointName;

	    public final String handler;

        public TransactionInfo(String fromActorId, String fromEndpointName, String toActorId, String toEndpointName, String handler) {
	        this.fromActorId = fromActorId;
	        this.fromEndpointName = fromEndpointName;
	        this.toActorId = toActorId;
	        this.toEndpointName = toEndpointName;
	        this.handler = handler;
        }
    }
}