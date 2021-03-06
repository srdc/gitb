package com.gitb.engine.actors.processors;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import com.gitb.core.StepStatus;
import com.gitb.engine.commands.interaction.StartCommand;
import com.gitb.engine.commands.interaction.StopCommand;
import com.gitb.engine.events.model.StatusEvent;
import com.gitb.engine.expr.ExpressionHandler;
import com.gitb.engine.testcase.TestCaseScope;
import com.gitb.tdl.WhileStep;
import com.gitb.types.DataType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by serbay on 9/12/14.
 *
 * While step executor actor
 */
public class WhileStepProcessorActor extends AbstractIterationStepActor<WhileStep> {

	public static final String NAME = "while-s-p";

	private ExpressionHandler expressionHandler;
	private Map<Integer, Integer> childActorUidIndexMap;
	private Map<Integer, ActorRef> iterationIndexActorMap;

	private boolean childrenHasError;

	public WhileStepProcessorActor(WhileStep step, TestCaseScope scope, String stepId){
		super(step, scope, stepId);
	}

	@Override
	protected void init() throws Exception {
		expressionHandler = new ExpressionHandler(scope);
		childActorUidIndexMap = new ConcurrentHashMap<>();
		iterationIndexActorMap = new ConcurrentHashMap<>();

		childrenHasError = false;
	}

	@Override
	protected void start() throws Exception {
		processing();
		boolean started = loop(0);
		if(!started) {
			completed(); // TODO send test step report
		}
	}

	@Override
	protected void stop() {
		StopCommand command = new StopCommand(scope.getContext().getSessionId());
		for(ActorRef child : getContext().getChildren()) {
			child.tell(command, self());
		}
	}

	@Override
	protected void handleStatusEvent(StatusEvent event) throws Exception {
//		super.handleStatusEvent(event);
        StepStatus status = event.getStatus();

		switch (status) {
			case ERROR:
				childrenHasError = true; // intentional fall through - no break statement
			case COMPLETED:
				int senderUid = getSender().path().uid();
				int iteration = childActorUidIndexMap.get(senderUid);

				boolean started = loop(iteration+1);

				if(!started) {
					if(childrenHasError) {
						childrenHasError();
					} else {
						completed();
					}
				}
		}
    }

	private boolean loop(int iteration) throws Exception {
		checkIteration(iteration);

		boolean condition = (boolean) expressionHandler
			.processExpression(step.getCond(), DataType.BOOLEAN_DATA_TYPE)
			.getValue();

		if(condition) {
			ActorRef iterationActor = SequenceProcessorActor.create(getContext(), step.getDo(), scope, stepId + ITERATION_OPENING_TAG + (iteration + 1) + ITERATION_CLOSING_TAG);

			childActorUidIndexMap.put(iterationActor.path().uid(), iteration);
			iterationIndexActorMap.put(iteration, iterationActor);

			StartCommand command = new StartCommand(scope.getContext().getSessionId());
			iterationActor.tell(command, self());
		}

		return condition;
	}

	public static ActorRef create(ActorContext context, WhileStep step, TestCaseScope scope, String stepId) throws Exception {
		return create(WhileStepProcessorActor.class, context, step, scope, stepId);
	}
}
