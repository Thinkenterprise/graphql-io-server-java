/**
 * *****************************************************************************
 *
 * <p>Design and Development by msg Applied Technology Research Copyright (c) 2019-2020 msg systems
 * ag (http://www.msg-systems.com/) All Rights Reserved.
 *
 * <p>Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * <p>The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * <p>THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * <p>****************************************************************************
 */
package com.graphqlio.server.subprotocols;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.graphqlio.wsf.converter.WsfAbstractConverter;

/**
 * websockethandler class for testing subprotocols
 *
 * @author Michael Schäfer
 * @author Torsten Kühnert
 */
public class SubprotocolsTestsHandler extends AbstractWebSocketHandler {

  private final Logger logger = LoggerFactory.getLogger(SubprotocolsTestsHandler.class);

  public int text_count = 0;
  public int cbor_count = 0;
  public int msgpack_count = 0;
  public int default_count = 0;

  public int count = 0;

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    logger.info("handleTextMessage");
    this.handlePayload(session, message.getPayload());
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message)
      throws Exception {
    logger.info("handleBinaryMessage");
    this.handlePayload(session, message.getPayload());
  }

  protected void handlePayload(WebSocketSession session, Object payload) throws Exception {
    logger.info("session.getAcceptedProtocol = " + session.getAcceptedProtocol());
    logger.info("payload = " + payload);

    if (WsfAbstractConverter.SUB_PROTOCOL_TEXT.equalsIgnoreCase(session.getAcceptedProtocol())) {
      this.text_count++;
      this.count++;
    } else if (WsfAbstractConverter.SUB_PROTOCOL_CBOR.equalsIgnoreCase(
        session.getAcceptedProtocol())) {
      this.cbor_count++;
      this.count++;
    } else if (WsfAbstractConverter.SUB_PROTOCOL_MSGPACK.equalsIgnoreCase(
        session.getAcceptedProtocol())) {
      this.msgpack_count++;
      this.count++;
    } else {
      this.default_count++;
      this.count++;
    }
  }
}
