package com.gitb.messaging.layer.transport.udp;

import com.gitb.core.Configuration;
import com.gitb.messaging.Message;
import com.gitb.messaging.layer.AbstractDatagramReceiver;
import com.gitb.messaging.layer.transport.tcp.TCPMessagingHandler;
import com.gitb.messaging.model.SessionContext;
import com.gitb.messaging.model.TransactionContext;
import com.gitb.types.BinaryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.util.List;

/**
 * Created by serbay.
 */
public class UDPReceiver extends AbstractDatagramReceiver {

	Logger logger = LoggerFactory.getLogger(UDPReceiver.class);

	protected UDPReceiver(SessionContext session, TransactionContext transaction) {
		super(session, transaction);
	}

	@Override
	public Message receive(List<Configuration> configurations) throws Exception {
		waitUntilMessageReceived();

		DatagramPacket packet = transaction.getParameter(DatagramPacket.class);

		logger.debug("Message received: " + packet);

		byte data[] = new byte[packet.getData().length];
		System.arraycopy(packet.getData(), 0, data, 0, packet.getData().length);

		BinaryType binaryData = new BinaryType();
		binaryData.setValue(data);

		Message message = new Message();
		message
			.getFragments()
			.put(UDPMessagingHandler.CONTENT_MESSAGE_FIELD_NAME, binaryData);

		return message;
	}
}
