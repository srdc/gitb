package com.gitb.messaging.layer.application.http;

import com.gitb.core.MessagingModule;
import com.gitb.messaging.IMessagingHandler;
import com.gitb.messaging.layer.AbstractMessagingHandler;
import com.gitb.messaging.model.tcp.ITransactionListener;
import com.gitb.messaging.model.tcp.ITransactionReceiver;
import com.gitb.messaging.model.tcp.ITransactionSender;
import com.gitb.messaging.model.SessionContext;
import com.gitb.messaging.model.TransactionContext;
import com.gitb.messaging.utils.MessagingHandlerUtils;
import org.kohsuke.MetaInfServices;

import java.io.IOException;

/**
 * Created by serbay on 9/23/14.
 */
@MetaInfServices(IMessagingHandler.class)
public class HttpMessagingHandler extends AbstractMessagingHandler {

	public static final String HTTP_HEADERS_FIELD_NAME = "http_headers";
	public static final String HTTP_BODY_FIELD_NAME = "http_body";
	public static final String HTTP_METHOD_FIELD_NAME = "http_method";
	public static final String HTTP_PATH_FIELD_NAME = "http_path";
    public static final String HTTP_PROTOCOL_VERSION_FIELD_NAME = "http_version";

	public static final String HTTP_URI_CONFIG_NAME = "http.uri";
	public static final String HTTP_METHOD_CONFIG_NAME = "http.method";
	public static final String HTTP_URI_EXTENSION_CONFIG_NAME = "http.uri.extension";

    public static final String HTTP_STATUS_CODE_CONFIG_NAME = "status.code";

	public static final String MODULE_DEFINITION_XML = "/http-messaging-definition.xml";

	private static MessagingModule module = MessagingHandlerUtils.readModuleDefinition(MODULE_DEFINITION_XML);

	@Override
	public MessagingModule getModuleDefinition() {
		return module;
	}

	@Override
	public ITransactionReceiver getReceiver(SessionContext sessionContext, TransactionContext transactionContext) throws IOException {
		return new HttpReceiver(sessionContext, transactionContext);
	}

	@Override
	public ITransactionSender getSender(SessionContext sessionContext, TransactionContext transactionContext) {
		return new HttpSender(sessionContext, transactionContext);
	}

    @Override
    public ITransactionListener getListener(SessionContext sessionContext, TransactionContext receiverTransactionContext, TransactionContext senderTransactionContext) {
        return new HttpListener(sessionContext, receiverTransactionContext, senderTransactionContext);
    }
}
