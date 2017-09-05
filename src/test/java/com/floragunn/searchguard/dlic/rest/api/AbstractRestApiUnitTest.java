/*
 * Copyright 2016 by floragunn UG (haftungsbeschränkt) - All rights reserved
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

import java.util.Arrays;
import java.util.Collection;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.junit.Assert;

import com.floragunn.searchguard.test.DynamicSgConfig;
import com.floragunn.searchguard.test.SingleClusterTest;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public abstract class AbstractRestApiUnitTest extends SingleClusterTest {

    protected RestHelper rh = null;
    protected boolean init = true;
    
    protected final void setup() throws Exception {
        final Settings nodeSettings = defaultNodeSettings(true);
        setup(Settings.EMPTY, new DynamicSgConfig(), nodeSettings, init);
        rh = restHelper();
    }
    
	protected void deleteUser(String username) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executeDeleteRequest("/_searchguard/api/user/" + username, new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void addUserWithPassword(String username, String password, int status) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executePutRequest("/_searchguard/api/user/" + username,
				"{\"password\": \"" + password + "\"}", new Header[0]);
		Assert.assertEquals(status, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void addUserWithPassword(String username, String password, String[] roles, int status) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		String payload = "{" + "\"password\": \"" + password + "\"," + "\"roles\": [";
		for (int i = 0; i < roles.length; i++) {
			payload += "\" " + roles[i] + " \"";
			if (i + 1 < roles.length) {
				payload += ",";
			}
		}
		payload += "]}";
		HttpResponse response = rh.executePutRequest("/_searchguard/api/user/" + username, payload, new Header[0]);
		Assert.assertEquals(status, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void addUserWithHash(String username, String hash) throws Exception {
		addUserWithHash(username, hash, HttpStatus.SC_OK);
	}

	protected void addUserWithHash(String username, String hash, int status) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executePutRequest("/_searchguard/api/user/" + username, "{\"hash\": \"" + hash + "\"}",
				new Header[0]);
		Assert.assertEquals(status, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void checkGeneralAccess(int status, String username, String password) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = false;
		Assert.assertEquals(status,
				rh.executeGetRequest("",
						encodeBasicHeader(username, password))
						.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected String checkReadAccess(int status, String username, String password, String indexName, String type,
			int id) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = false;
		String action = indexName + "/" + type + "/" + id;
		HttpResponse response = rh.executeGetRequest(action,
				encodeBasicHeader(username, password));
		int returnedStatus = response.getStatusCode();
		Assert.assertEquals(status, returnedStatus);
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
		return response.getBody();

	}

	protected String checkWriteAccess(int status, String username, String password, String indexName, String type,
			int id) throws Exception {

		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = false;
		String action = indexName + "/" + type + "/" + id;
		String payload = "{\"value\" : \"true\"}";
		HttpResponse response = rh.executePutRequest(action, payload,
				encodeBasicHeader(username, password));
		int returnedStatus = response.getStatusCode();
		Assert.assertEquals(status, returnedStatus);
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
		return response.getBody();
	}

	protected void setupStarfleetIndex() throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		rh.executePutRequest("sf", null, new Header[0]);
		rh.executePutRequest("sf/ships/0", "{\"number\" : \"NCC-1701-D\"}", new Header[0]);
		rh.executePutRequest("sf/public/0", "{\"some\" : \"value\"}", new Header[0]);
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}
	
	/*protected void setupSearchGuardIndex() {
		Settings tcSettings = Settings.builder()
		        //.put("cluster.name", ClusterHelper.clustername)
				.put(defaultNodeSettings(false))
				.put("searchguard.ssl.transport.keystore_filepath",
						FileHelper.getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk").put("path.home", ".").build();

		try (TransportClient tc = new TransportClientImpl(tcSettings,asCollection(Netty4Plugin.class, SearchGuardPlugin.class))) {

			log.debug("Start transport client to init");

			tc.addTransportAddress(new TransportAddress(new InetSocketAddress(clusterInfo.nodeHost, clusterInfo.nodePort)));
			Assert.assertEquals(clusterInfo.numNodes,
					tc.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet().getNodes().size());

			tc.admin().indices().create(new CreateIndexRequest("searchguard")).actionGet();


		}
		
	}*/

	protected Settings defaultNodeSettings(boolean enableRestSSL) {
		Settings.Builder builder = Settings.builder();

		if (enableRestSSL) {
			builder.put("searchguard.ssl.http.enabled", true)
					.put("searchguard.ssl.http.keystore_filepath",
							FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
					.put("searchguard.ssl.http.truststore_filepath",
							FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"));
		}
		return builder.build();
	}
	
	protected static class TransportClientImpl extends TransportClient {

        public TransportClientImpl(Settings settings, Collection<Class<? extends Plugin>> plugins) {
            super(settings, plugins);
        }

        public TransportClientImpl(Settings settings, Settings defaultSettings, Collection<Class<? extends Plugin>> plugins) {
            super(settings, defaultSettings, plugins, null);
        }       
    }
	
	protected static Collection<Class<? extends Plugin>> asCollection(Class<? extends Plugin>... plugins) {
        return Arrays.asList(plugins);
    }
}
