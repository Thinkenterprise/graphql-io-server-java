/*******************************************************************************
 * *
 * **  Design and Development by msg Applied Technology Research
 * **  Copyright (c) 2019-2020 msg systems ag (http://www.msg-systems.com/)
 * **  All Rights Reserved.
 * ** 
 * **  Permission is hereby granted, free of charge, to any person obtaining
 * **  a copy of this software and associated documentation files (the
 * **  "Software"), to deal in the Software without restriction, including
 * **  without limitation the rights to use, copy, modify, merge, publish,
 * **  distribute, sublicense, and/or sell copies of the Software, and to
 * **  permit persons to whom the Software is furnished to do so, subject to
 * **  the following conditions:
 * **
 * **  The above copyright notice and this permission notice shall be included
 * **  in all copies or substantial portions of the Software.
 * **
 * **  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * **  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * **  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * **  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * **  CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * **  TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * **  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * *
 ******************************************************************************/
package com.graphqlio.server.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.graphqlio.gts.actuator.GtsCounter;
import com.graphqlio.gts.evaluation.GtsEvaluation;
import com.graphqlio.gts.exceptions.GtsSubscriptionTypeException;
import com.graphqlio.gts.tracking.GtsConnection;
import com.graphqlio.gts.tracking.GtsScope;
import com.graphqlio.server.execution.GsExecutionStrategy;
import com.graphqlio.server.graphql.schema.GsGraphQLSchemaCreator;
import com.graphqlio.server.server.GsContext;
import com.graphqlio.wsf.converter.WsfConverter;
import com.graphqlio.wsf.converter.WsfFrameToMessageConverter;
import com.graphqlio.wsf.domain.WsfFrame;
import com.graphqlio.wsf.domain.WsfFrameType;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import graphql.GraphQLException;

/**
 * Class used to process any incoming message sent by clients via WebSocket
 * supports subprotocols (CBOR, MsgPack, Text)
 * triggers process to indicate outdating queries and notifies clients 
 *
 * @author Michael Schäfer
 * @author Dr. Edgar Müller
 * @author Torsten Kühnert
 */

public class GsWebSocketHandler extends AbstractWebSocketHandler implements SubProtocolCapable {

	public static final String SUB_PROTOCOL_TEXT = "text";
	public static final String SUB_PROTOCOL_CBOR = "cbor";
	public static final String SUB_PROTOCOL_MSGPACK = "msgpack";

	private final Logger logger = LoggerFactory.getLogger(GsWebSocketHandler.class);

	private final Map<String, GtsConnection> webSocketConnections = new ConcurrentHashMap<>();
	private final Map<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();

	private final WsfFrameToMessageConverter requestConverter;

	private final WsfFrameToMessageConverter responseConverter;

	private final WsfFrameToMessageConverter notifyerConverter;

	private final GsExecutionStrategy graphQLIOQueryExecution;

	private final GtsEvaluation graphQLIOEvaluation;

	private final GsGraphQLSchemaCreator gsGraphQLSchemaCreator;

	private final GtsCounter gsGtsCounter;
	
	@Autowired
	public GsWebSocketHandler(GsExecutionStrategy executionStrategy,
			GtsEvaluation evaluation, GsGraphQLSchemaCreator schemaCreator, GtsCounter gtsCounter,
			WsfFrameToMessageConverter requestConverter,
			WsfFrameToMessageConverter responseConverter,
			WsfFrameToMessageConverter notifyerConverter) {

		this.requestConverter = requestConverter;
		this.responseConverter = responseConverter;
		this.notifyerConverter = notifyerConverter;

		graphQLIOQueryExecution = executionStrategy;
		graphQLIOEvaluation = evaluation;
		gsGraphQLSchemaCreator = schemaCreator;
		gsGtsCounter = gtsCounter;
	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {

		
		// Client controls which protocol is used
		// server just checks if protocol is supported.
		// if there is no protocol information in message header 
		// server tries to handle message as text message 
		
		
		logger.info("GraphQLIO handleMessage received graphqlio message::getAcceptedProtocol = " + session.getAcceptedProtocol());

		if (SUB_PROTOCOL_TEXT.equalsIgnoreCase(session.getAcceptedProtocol())
				&& message instanceof TextMessage) {

			this.handleTextMessage(session, (TextMessage) message);

		} else if (SUB_PROTOCOL_CBOR.equalsIgnoreCase(session.getAcceptedProtocol())
				&& message instanceof BinaryMessage) {

			this.handleCborMessage(session, (BinaryMessage) message);

		} else if (SUB_PROTOCOL_MSGPACK.equalsIgnoreCase(session.getAcceptedProtocol())
				&& message instanceof BinaryMessage) {

			this.handleMsgPackMessage(session, (BinaryMessage) message);

		} else {
			// super.handleMessage(session, message);
			this.handleTextMessage(session, (TextMessage) message);
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		logger.info("GraphQLIO handleTextMessage session :" + session);
		logger.info("GraphQLIO handleTextMessage session ID :" + session.getId());
		logger.info("GraphQLIO handleTextMessage this :" + this);
		logger.info("GraphQLIO handleTextMessage Thread :" + Thread.currentThread());

		this.handleStringMessage(session, message.getPayload());
	}

	protected void handleCborMessage(WebSocketSession session, BinaryMessage message) throws Exception {

		logger.info("GraphQLIO handleCborMessage session :" + session);
		logger.info("GraphQLIO handleCborMessage session ID :" + session.getId());
		logger.info("GraphQLIO handleCborMessage this :" + this);
		logger.info("GraphQLIO handleCborMessage Thread :" + Thread.currentThread());

		String input = getFromCbor(message);
		logger.info("cbor.input = " + input);

		if (input != null) {
			this.handleStringMessage(session, input);
		} else {
			logger.info("GraphQLIO handleCborMessage : NO valid CBOR message");
		}
	}

	protected void handleMsgPackMessage(WebSocketSession session, BinaryMessage message) throws Exception {

		logger.info("GraphQLIO handleMsgPackMessage session :" + session);
		logger.info("GraphQLIO handleMsgPackMessage session ID :" + session.getId());
		logger.info("GraphQLIO handleMsgPackMessage this :" + this);
		logger.info("GraphQLIO handleMsgPackMessage Thread :" + Thread.currentThread());

		String input = getFromMsgPack(message);
		logger.info("msgPack.input = " + input);

		this.handleStringMessage(session, input);
	}

	protected void handleStringMessage(WebSocketSession session, String message) throws Exception {

		logger.info("GraphQLIO handle String message = " + message);

		// Convert Frame to Message
		WsfFrame requestMessage = requestConverter.convert(message);
		
		// Get the Connection, create a Scope and push it to the context
		GtsConnection connection = webSocketConnections.get(session.getId());
		GtsScope scope = null;
		
		/// check if request message is a Subscription message (any of unsubscribe, pause, resume) and retrieve scopeId 
		/// returns valid UUID as String, null otherwise
		
		/// ToDo: check if Scope generation could be delegated to (Gts-)Resolver
		/// and gts library holds GtsConnection map resp. GtsScope list 
		
		String scopeId = null;
		try {
			scopeId = this.getSubscriptionScopeId(requestMessage.getData());
		}
		catch ( GtsSubscriptionTypeException e ){
			
			String graphQLConformError =
					String.format("{\"errors\":[{\"message\":\"%s\"}]", e.getLocalizedMessage());
			
			WsfFrame errorMessage = WsfFrame.builder().fid(requestMessage.getFid()).rid(requestMessage.getRid())
					.type(WsfFrameType.GRAPHQLRESPONSE).data(graphQLConformError).build();
			
			String answerFrame = responseConverter.convert(errorMessage);
			// Send back
			sendAnswerBackToClient(session, answerFrame);
			return;
		}

		if (scopeId != null) {
			scope = connection.getScopeById(scopeId);
		}
		else {
			scope = GtsScope.builder()
										.withQuery(requestMessage.getData())
										.withConnectionId(connection.getConnectionId())
										.withGtsCounter(gsGtsCounter)
										.build();
			connection.addScope(scope);			
		}

		// Create Context Information for Execution
		GsContext graphQLIOContext = GsContext.builder().webSocketSession(session)
				.graphQLSchema(gsGraphQLSchemaCreator.getGraphQLSchema()).requestMessage(requestMessage).scope(scope)
				.build();

		// Execute Message
		// Exceptions are catched inside, transformed to GraphQLErrors and finally put into context response message
		logger.info("GraphQLIO start executing graphQLIOContext ....");
		graphQLIOQueryExecution.execute(graphQLIOContext);
		logger.info("GraphQLIO finished execution of graphQLIOContext ....");

		// Convert Result Message to Frame
		String answerFrame = responseConverter.convert(graphQLIOContext.getResponseMessage());

		// Send back
		sendAnswerBackToClient(session, answerFrame);
		
		try {
			
			logger.info("GraphQLIOEvaluation Evaluating outdated Scopes ....");
			
			// Evaluate Subscriptions and notify clients
			List<String> sids = graphQLIOEvaluation.evaluateOutdatedSids(graphQLIOContext.getScope());

			logger.info("GraphQLIOEvaluation Finished Evaluating outdated Scopes ....");
			
			
			sids.forEach(sid -> {
				logger.info(String.format("GraphQLIO Scope Evaluation: Scope (%s) outdated", sid));
			});
			
			if ( !sids.isEmpty()) {
				Map<String, Set<String>> sids4cid = graphQLIOEvaluation.evaluateOutdatedsSidsPerCid(sids,
						webSocketConnections.values());			
				
				if ( sids4cid.size() > 0)
					sendNotifierMessageToClients(sids4cid, requestMessage);
			}
		}
		catch (GraphQLException e) {
			logger.error(e.toString());				
///			ToDo: Shall we notify client???			
		}
	}

	private void sendAnswerBackToClient(WebSocketSession session, String answerFrame) throws Exception {

		logger.info(String.format("GraphQLIO sendAnswerBackToClient::getAcceptedProtocol = %s", session.getAcceptedProtocol()));
		logger.info(String.format("GraphQLIO sendAnswerBackToClient::answerFrame = %s", answerFrame));

		if (SUB_PROTOCOL_TEXT.equalsIgnoreCase(session.getAcceptedProtocol())) {

			session.sendMessage(new TextMessage(answerFrame));

		} else if (SUB_PROTOCOL_CBOR.equalsIgnoreCase(session.getAcceptedProtocol())) {

			session.sendMessage(createFromStringCbor(answerFrame));

		} else if (SUB_PROTOCOL_MSGPACK.equalsIgnoreCase(session.getAcceptedProtocol())) {

			session.sendMessage(createFromStringMsgPack(answerFrame));

		} else {
			// DEFAULT is TEXT:
			session.sendMessage(new TextMessage(answerFrame));
		}
	}

	public static String getFromCbor(BinaryMessage message) throws CborException {
		List<DataItem> dataItems = CborDecoder.decode(message.getPayload().array());

		// wenn keine Exception:
		if (dataItems == null || dataItems.isEmpty()) {
			// logging

		} else if (!dataItems.isEmpty()) {
			if (dataItems.size() >= 2) {
				// logging
			}

			DataItem dataItem = dataItems.get(0);
			// logging

			if (dataItem instanceof ByteString) {
				String input = new String(((ByteString) dataItem).getBytes());
				// logging

				return input;

			} else {
				// logging
			}
		}

		// logging
		return null;
	}

	public static String getFromMsgPack(BinaryMessage message) throws IOException {
		MessageUnpacker unpacker = null;
		String input = null;
		try {
			unpacker = MessagePack.newDefaultUnpacker(message.getPayload().array());
			input = unpacker.unpackString();
			// logging			
		}
		finally {
			if (unpacker != null)
				unpacker.close();			
		}

		return input;
	}

	public static BinaryMessage createFromStringCbor(String message) throws CborException {
		byte[] bytes = message.getBytes();
		DataItem dataItem = new ByteString(bytes);

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		new CborEncoder(os).encode(dataItem);

		return new BinaryMessage(os.toByteArray());
	}

	public static BinaryMessage createFromStringMsgPack(String message) throws IOException {
		MessageBufferPacker packer = null;
		try {
			packer = MessagePack.newDefaultBufferPacker();
			packer.packString(message);
		}
		finally {
			if (packer != null)
				packer.close();			
		}
		return new BinaryMessage(packer.toByteArray());
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		webSocketConnections.put(session.getId(), 
				GtsConnection.builder()
					.fromSession(session)
					.withGtsCounter(gsGtsCounter)
					.build());
		webSocketSessions.put(session.getId(), session);		
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		GtsConnection connection = webSocketConnections.get(session.getId());
		if (connection != null) {

			///   remove entries from key value store
	    	this.graphQLIOEvaluation.onCloseConnection(connection.getConnectionId());
			
			/// remove scopes from list in connections
			connection.onClose();
			
		}		
		webSocketConnections.remove(session.getId());
		webSocketSessions.remove(session.getId());
	}

	private void sendNotifierMessageToClients(Map<String, Set<String>> sids4cid, WsfFrame requestMessage)
			throws Exception {

		Set<String> cids = sids4cid.keySet();

		for (String cid : cids) {
			WsfFrame message = WsfFrame.builder().fid(requestMessage.getFid()).rid(requestMessage.getRid())
					.type(WsfFrameType.GRAPHQLNOTIFIER).data(createData(sids4cid.get(cid))).build();
			String frame = notifyerConverter.convert(message);
			WebSocketSession sessionForCid = webSocketSessions.get(cid);
			if (sessionForCid != null ) {
				sendAnswerBackToClient(sessionForCid, frame);
			}
		}
	}

	private String surroundWithQuotes(String value) {
		return "\"" + value + "\"";
	}

	private String createData(Set<String> set) {
		String result = set.isEmpty() ? "" : surroundWithQuotes(String.join(surroundWithQuotes(", "), set));
		return "{" + surroundWithQuotes("data") + ":[" +  result + "]}";
	}

	/// check if message is a "Subscription - mutation" and contains valid UUID 
	private String getSubscriptionScopeId( String requestMessage ) {
		
		final String REQUEST_MESSAGE_PART_TYPE_QUERY = "query";
		final String REQUEST_MESSAGE_PART_TYPE_MUTATION = "mutation";
		final String REQUEST_MESSAGE_PART_TYPE_SUBSCRIPTION = "_Subscription";
		final String REQUEST_MESSAGE_PART_SCOPE_ID = "sid:";
		final String REQUEST_MESSAGE_PART_METHOD_UNSUBSCRIBE = "unsubscribe";
		final String REQUEST_MESSAGE_PART_METHOD_PAUSE = "pause";
		final String REQUEST_MESSAGE_PART_METHOD_RESUME = "resume";
				
		String message = StringUtils.deleteAny(requestMessage,  "\"");
		message = StringUtils.deleteAny(message,  " ");
		
		if (message.contains(REQUEST_MESSAGE_PART_TYPE_SUBSCRIPTION)) {
			if (message.contains(REQUEST_MESSAGE_PART_TYPE_MUTATION)) {
				int indexOf = message.indexOf(REQUEST_MESSAGE_PART_SCOPE_ID);
				if (indexOf > 0) {
					indexOf += REQUEST_MESSAGE_PART_SCOPE_ID.length();
					
					String uuidString = null;				
					try {
						uuidString = message.substring(indexOf, indexOf+36);
						UUID uuid = isValidUUID(uuidString);
						if (uuid != null) {
							return uuidString;						
						}
						else {
							throw new GtsSubscriptionTypeException(String.format("GsWebSocketHandler.getSubscriptionScopeId: uuidString (%s) does not represent a valid UUID", uuidString));
						}																
					}
					catch( IndexOutOfBoundsException e) {
						throw new GtsSubscriptionTypeException(String.format("GsWebSocketHandler.getSubscriptionScopeId: uuidString (%s) does not represent a valid UUID", uuidString));					
					}						
				}
				else {  
					//// no scope id (sid) parameter				
					throw new GtsSubscriptionTypeException("GsWebSocketHandler.getSubscriptionScopeId: expect parameter <sid> for _Subscription");				
				}
			}
			else {
				if (((message.contains(REQUEST_MESSAGE_PART_METHOD_UNSUBSCRIBE) ||
						  message.contains(REQUEST_MESSAGE_PART_METHOD_PAUSE)	 ||
						  message.contains(REQUEST_MESSAGE_PART_METHOD_RESUME) )) ) {	
					//// query/unsubscribe/pause/resume   not supported
					throw new GtsSubscriptionTypeException("GsWebSocketHandler.getSubscriptionScopeId: expect _Subscription of type MUTATION");				
				}
				else {
					/// regular query message
					return null;
				}				
			}
		}
		
		return null;
	}
	
	/// helper function
	private UUID isValidUUID(String uuidString) {
		UUID resultUUID = null;
		try {
			resultUUID = UUID.fromString(uuidString);   //throws exception if string does not represent a valid UUID 
		}
		catch( IllegalArgumentException e) {
			resultUUID = null;
		}						
		return resultUUID;	
	}
	
	@Override
	public List<String> getSubProtocols() {
		return Arrays.asList(SUB_PROTOCOL_TEXT, SUB_PROTOCOL_CBOR, SUB_PROTOCOL_MSGPACK);
	}
	
}
