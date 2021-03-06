package com.gitb.engine.actors.processors;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.dispatch.Futures;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import com.gitb.core.ErrorCode;
import com.gitb.core.StepStatus;

import com.gitb.engine.actors.ActorSystem;
import com.gitb.engine.events.model.ErrorStatusEvent;
import com.gitb.exceptions.GITBEngineInternalError;
import com.gitb.engine.messaging.MessagingContext;
import com.gitb.engine.messaging.TransactionContext;
import com.gitb.engine.testcase.TestCaseContext;
import com.gitb.engine.testcase.TestCaseScope;
import com.gitb.messaging.IMessagingHandler;
import com.gitb.messaging.Message;
import com.gitb.messaging.MessagingReport;
import com.gitb.tdl.Send;
import com.gitb.tr.TestResultType;
import com.gitb.tr.TestStepReportType;
import com.gitb.types.MapType;
import com.gitb.utils.BindingUtils;
import com.gitb.utils.ErrorUtils;
import scala.concurrent.Future;
import scala.concurrent.Promise;

import java.util.concurrent.Callable;

/**
 * Created by serbay on 9/30/14.
 *
 * Send step processor actor
 */
public class SendStepProcessorActor extends AbstractMessagingStepProcessorActor<Send> {
	public static final String NAME = "send-p";

	private MessagingContext messagingContext;
	private TransactionContext transactionContext;

	private Promise<TestStepReportType> promise;
	private Future<TestStepReportType> future;

	public SendStepProcessorActor(Send step, TestCaseScope scope, String stepId) {
		super(step, scope, stepId);
	}

    @Override
	protected void init() throws Exception {

		final ActorContext context = getContext();

		promise = Futures.promise();

		promise.future().onSuccess(new OnSuccess<TestStepReportType>() {
			@Override
			public void onSuccess(TestStepReportType result) throws Throwable {
				if(result != null) {
					if (result.getResult() == TestResultType.SUCCESS) {
						updateTestStepStatus(context, StepStatus.COMPLETED, result);
					} else {
						updateTestStepStatus(context, StepStatus.ERROR, result);
					}
				} else {
					updateTestStepStatus(context, StepStatus.COMPLETED, null);
				}
			}
		}, context.dispatcher());

		promise.future().onFailure(new OnFailure() {
			@Override
			public void onFailure(Throwable failure) throws Throwable {
				updateTestStepStatus(context, new ErrorStatusEvent(failure), null, true);
			}
		}, context.dispatcher());
	}

	@Override
	protected void start() throws Exception {
		processing();
        TestCaseContext testCaseContext = scope.getContext();

        for(MessagingContext mc : testCaseContext.getMessagingContexts()) {
            if(mc.getTransaction(step.getTxnId()) != null) {
                messagingContext = mc;
                break;
            }
        }

        transactionContext = messagingContext.getTransaction(step.getTxnId());

		final IMessagingHandler messagingHandler = messagingContext.getHandler();

		if(messagingHandler != null) {
			future = Futures.future(new Callable<TestStepReportType>() {
				@Override
				public TestStepReportType call() throws Exception {

					Message message = new Message();

					boolean isNameBinding = BindingUtils.isNameBinding(step.getInput());

					if(isNameBinding) {
						setInputWithNameBinding(message, step.getInput(), getRequiredInputs(messagingHandler));
					} else {
						setInputWithModuleDefinition(message, step.getInput(), getRequiredInputs(messagingHandler));
					}

					MessagingReport report =
						messagingHandler
							.sendMessage(
								messagingContext.getSessionId(),
								transactionContext.getTransactionId(),
								step.getConfig(),
								message
							);

                    if(report != null) {
                        //id parameter must be set for Send steps, so that sent message
                        //is saved to scope
                        if(step.getId() != null && report.getMessage() != null) {
                            Message sentMessage = report.getMessage();
                            MapType map = generateOutputWithMessageFields(sentMessage);

                            scope
                                .createVariable(step.getId())
                                .setValue(map);
                        }

                        return report.getReport();
                    } else {
                        return null;
                    }
				}
			}, getContext().dispatcher());

			future.onSuccess(new OnSuccess<TestStepReportType>() {
				@Override
				public void onSuccess(TestStepReportType result) throws Throwable {
					promise.trySuccess(result);
				}
			}, getContext().dispatcher());

			future.onFailure(new OnFailure() {
				@Override
				public void onFailure(Throwable failure) throws Throwable {
					promise.tryFailure(failure);
				}
			}, getContext().dispatcher());
		}

	}

	@Override
	protected void stop() {
		if(promise != null) {
			boolean stopped = promise.tryFailure(new GITBEngineInternalError(ErrorUtils.errorInfo(ErrorCode.CANCELLATION, "Test step ["+stepId+"] is cancelled.")));
		}
	}

    @Override
    protected MessagingContext getMessagingContext() {
        return messagingContext;
    }

    @Override
    protected TransactionContext getTransactionContext() {
        return transactionContext;
    }

	public static ActorRef create(ActorContext context, Send step, TestCaseScope scope, String stepId) throws Exception {
		return context.actorOf(props(SendStepProcessorActor.class, step, scope, stepId).withDispatcher(ActorSystem.BLOCKING_DISPATCHER), getName(NAME));
	}
}
