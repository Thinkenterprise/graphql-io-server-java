/*
**  Design and Development by msg Applied Technology Research
**  Copyright (c) 2019-2020 msg systems ag (http://www.msg-systems.com/)
**  All Rights Reserved.
** 
**  Permission is hereby granted, free of charge, to any person obtaining
**  a copy of this software and associated documentation files (the
**  "Software"), to deal in the Software without restriction, including
**  without limitation the rights to use, copy, modify, merge, publish,
**  distribute, sublicense, and/or sell copies of the Software, and to
**  permit persons to whom the Software is furnished to do so, subject to
**  the following conditions:
**
**  The above copyright notice and this permission notice shall be included
**  in all copies or substantial portions of the Software.
**
**  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
**  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
**  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
**  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
**  CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
**  TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
**  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package com.graphqlio.server.messages;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.graphqlio.server.handler.GsWebSocketHandler;
import com.graphqlio.server.helpers.FlightTest;

/**
 * websockethandler class for testing queries, mutations, subscriptions and
 * messages with subprotocols
 *
 * @author Michael Schäfer
 * @author Torsten Kühnert
 */

public class MessagesTestsHandler extends AbstractWebSocketHandler {

	public int text_count = 0;
	public int cbor_count = 0;
	public int msgpack_count = 0;
	public int default_count = 0;

	public int count = 0;

	public List<FlightTest> routes = new ArrayList<FlightTest>();

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
		if (GsWebSocketHandler.SUB_PROTOCOL_TEXT.equalsIgnoreCase(session.getAcceptedProtocol())) {
			this.text_count++;
			this.count++;
			String msg = ((TextMessage) message).getPayload();
			this.addFlights(msg);

		} else if (GsWebSocketHandler.SUB_PROTOCOL_CBOR.equalsIgnoreCase(session.getAcceptedProtocol())) {
			this.cbor_count++;
			this.count++;
			String msg = GsWebSocketHandler.getFromCbor((BinaryMessage) message);
			this.addFlights(msg);

		} else if (GsWebSocketHandler.SUB_PROTOCOL_MSGPACK.equalsIgnoreCase(session.getAcceptedProtocol())) {
			this.msgpack_count++;
			this.count++;
			String msg = GsWebSocketHandler.getFromMsgPack((BinaryMessage) message);
			this.addFlights(msg);

		} else {
			this.default_count++;
			this.count++;
			String msg = ((TextMessage) message).getPayload();
			this.addFlights(msg);
		}
	}

	// [1,1,"GRAPHQL-RESPONSE",{"data":{"routes":[{"flightNumber":"LH2122","departure":"MUC","destination":"BRE"},{"flightNumber":"LH2084","departure":"CGN","destination":"BER"}]}}]
	// [1,1,"GRAPHQL-RESPONSE",{"data":{"updateRoute":{"flightNumber":"LH2084","departure":"HAM","destination":"MUC"}}}]

	private void addFlights(String msg) throws JSONException {
		int pos = msg.indexOf("{\"data\":");
		if (pos > 0) {
			String jsonStr = msg.substring(pos);
			jsonStr = jsonStr.substring(0, jsonStr.length() - 1);
			// System.out.println("jsonStr = " + jsonStr);

			JSONObject jsonObj = new JSONObject(jsonStr);
			JSONObject dataObj = jsonObj.getJSONObject("data");

			if (dataObj.has("routes")) {
				JSONArray routesArr = dataObj.getJSONArray("routes");

				for (int i = 0; i < routesArr.length(); i++) {
					JSONObject flightObj = routesArr.getJSONObject(i);

					FlightTest newRoute = new FlightTest(flightObj.toString());
					this.routes.add(newRoute);
				}

			} else if (dataObj.has("updateRoute")) {
				JSONObject flightObj = dataObj.getJSONObject("updateRoute");

				FlightTest newRoute = new FlightTest(flightObj.toString());
				this.routes.add(newRoute);
			}
		}
	}

}
