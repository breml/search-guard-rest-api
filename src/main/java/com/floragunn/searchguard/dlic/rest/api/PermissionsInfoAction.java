/*
 * Copyright 2017 by floragunn GmbH - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use. 
 * For commercial use in a production environment you have to obtain a license 
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.dlic.rest.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.configuration.PrivilegesEvaluator;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.user.User;

/**
 * Provides the evaluated REST API permissions for the currently logged in user 
 */
public class PermissionsInfoAction extends BaseRestHandler {

	private final RestApiPrivilegesEvaluator restApiPrivilegesEvaluator;
	private final ThreadPool threadPool;
	private final PrivilegesEvaluator privilegesEvaluator;
	
	protected PermissionsInfoAction(final Settings settings, final Path configPath, final RestController controller, final Client client,
			final AdminDNs adminDNs, final IndexBaseConfigurationRepository cl, final ClusterService cs,
			final PrincipalExtractor principalExtractor, final PrivilegesEvaluator privilegesEvaluator, ThreadPool threadPool, AuditLog auditLog) {
		super(settings);
		controller.registerHandler(Method.GET, "/_searchguard/api/permissionsinfo", this);
		this.threadPool = threadPool;
		this.privilegesEvaluator = privilegesEvaluator;
		this.restApiPrivilegesEvaluator = new RestApiPrivilegesEvaluator(settings, adminDNs, privilegesEvaluator, principalExtractor, configPath, threadPool);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
		switch (request.method()) {
		case GET:
			return handleGet(request, client);
		default:
			throw new IllegalArgumentException(request.method() + " not supported");
		}
	}

	private RestChannelConsumer handleGet(RestRequest request, NodeClient client) throws IOException {
		
        return new RestChannelConsumer() {

            @Override
            public void accept(RestChannel channel) throws Exception {
                XContentBuilder builder = channel.newBuilder(); //NOSONAR
                BytesRestResponse response = null;
                
                try {

            		final User user = (User) threadPool.getThreadContext().getTransient(ConfigConstants.SG_USER);
            		final TransportAddress remoteAddress = (TransportAddress) threadPool.getThreadContext()
            				.getTransient(ConfigConstants.SG_REMOTE_ADDRESS);
            		Set<String> userRoles = privilegesEvaluator.mapSgRoles(user, remoteAddress);
            		Boolean hasApiAccess = restApiPrivilegesEvaluator.currentUserHasRestApiAccess(userRoles);
            		Map<Endpoint, List<Method>> disabledEndpoints = restApiPrivilegesEvaluator.getDisabledEndpointsForCurrentUser(user.getName(), userRoles);

                    builder.startObject();
                    builder.field("user", user);
                    builder.field("user_name", user==null?null:user.getName()); //NOSONAR
                    builder.field("has_api_access", hasApiAccess);
                    builder.startObject("disabled_endpoints");
                    for(Entry<Endpoint, List<Method>>  entry : disabledEndpoints.entrySet()) {
                    	builder.field(entry.getKey().name(), entry.getValue());
                    }
                    builder.endObject();
                    builder.endObject();
                    response = new BytesRestResponse(RestStatus.OK, builder);
                } catch (final Exception e1) {
                    builder = channel.newBuilder(); //NOSONAR
                    builder.startObject();
                    builder.field("error", e1.toString());
                    builder.endObject();
                    response = new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, builder);
                } finally {
                    if(builder != null) {
                        builder.close();
                    }
                }

                channel.sendResponse(response);
            }
        };

	}

}
