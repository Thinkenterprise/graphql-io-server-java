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
package com.thinkenterprise.graphqlio.server.gs.autoconfiguration;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.thinkenterprise.graphqlio.server.gs.actuator.custom.GsGraphqlioCounterEndpoint;
import com.thinkenterprise.graphqlio.server.gs.actuator.metrics.GsGraphqlioMeterRegistryCounter;
import com.thinkenterprise.graphqlio.server.gs.execution.GsExecutionStrategy;
import com.thinkenterprise.graphqlio.server.gs.graphql.GsGraphQLExecution;
import com.thinkenterprise.graphqlio.server.gs.graphql.GsGraphQLService;
import com.thinkenterprise.graphqlio.server.gs.graphql.schema.GsGraphQLSchemaCreator;
import com.thinkenterprise.graphqlio.server.gs.graphql.schema.GsGraphQLSimpleSchemaCreator;
import com.thinkenterprise.graphqlio.server.gs.handler.GsWebSocketHandler;
import com.thinkenterprise.graphqlio.server.gts.actuator.GtsCounter;
import com.thinkenterprise.graphqlio.server.gts.actuator.GtsCounterNotification;
import com.thinkenterprise.graphqlio.server.gts.evaluation.GtsEvaluation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Class to automatically configure the beans for the GraphQL IO Server library based on conditions 
 * and to configure processing WebSocket requests
 *
 * @author Michael Schäfer
 * @author Dr. Edgar Müller
 */


@Configuration
@EnableConfigurationProperties(GsAutoConfiguration.class)
@ConfigurationProperties(prefix = "graphqlio.server")
@EnableWebSocket
public class GsAutoConfiguration implements WebSocketConfigurer {

	@Autowired
	private GsProperties gsProperties;
		
	@Autowired
	private GsWebSocketHandler handler;
	
	@Bean
	@ConditionalOnMissingBean
	public GsGraphQLSchemaCreator gsGraphQLSchemaCreator() {
		return new GsGraphQLSimpleSchemaCreator();
	}

	@Bean
	@ConditionalOnMissingBean
	public GsGraphQLService gsGraphQLService() {
		return new GsGraphQLService();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public GsExecutionStrategy gsGraphQLExecution() {
		return new GsGraphQLExecution();
	}
	
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(
		    value="graphqliocounter", 
		    havingValue = "true")	
	public GsGraphqlioCounterEndpoint gsGraphqlioGtsCounter(GtsCounter gtsCounter) {
		return new GsGraphqlioCounterEndpoint(gtsCounter);
	}
	
	@Bean
	@ConditionalOnMissingBean
	public GsGraphqlioMeterRegistryCounter gsMetricsGtsCounter(MeterRegistry simpleRegistry, GtsCounter gtsCounter) {
		return new GsGraphqlioMeterRegistryCounter(simpleRegistry, gtsCounter);
	}
	
	
	@Bean
	public ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).registerModule(new Jdk8Module());
        
        InjectableValues.Std injectableValues = new InjectableValues.Std();
        injectableValues.addValue(ObjectMapper.class, mapper);
        mapper.setInjectableValues(injectableValues);

        return mapper;
    }
		
	@Bean
	@ConditionalOnMissingBean
	public GsWebSocketHandler gsWebSocketHandler( GsExecutionStrategy gsExecutionStategy
												, GtsEvaluation gtsEvaluation
												, GsGraphQLSchemaCreator gsSchemaCreator
												, GtsCounter gsGtsCounter
												) {
		return new GsWebSocketHandler	( gsExecutionStategy
										, gtsEvaluation
										, gsSchemaCreator
										, gsGtsCounter
				);

	}
	
	

    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {	 
    	registry.addHandler(this.handler, gsProperties.getEndpoint());   
	}
    
}
