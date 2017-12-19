/*
 * Copyright 2016-2017 by floragunn GmbH - All rights reserved
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

import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;


public class IndexMissingTest extends AbstractRestApiUnitTest {	

	@Test
	public void testGetConfiguration() throws Exception {
	    // don't setup index for this test
	    init = false;
		setup();

		// test with no SG index at all
		testHttpOperations();
		
	}
	
	protected void testHttpOperations() throws Exception {
		
		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// GET configuration
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		String errorString = response.getBody();
		Assert.assertEquals("Search Guard index not initialized (SG11)", errorString);
	
		// GET roles
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		errorString = response.getBody();
		Assert.assertEquals("Search Guard index not initialized (SG11)", errorString);

		// GET rolesmapping
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		errorString = response.getBody();
		Assert.assertEquals("Search Guard index not initialized (SG11)", errorString);
		
		// GET actiongroups
		response = rh.executeGetRequest("_searchguard/api/actiongroup/READ");
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		errorString = response.getBody();
		Assert.assertEquals("Search Guard index not initialized (SG11)", errorString);

		// GET internalusers
		response = rh.executeGetRequest("_searchguard/api/user/picard");
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		errorString = response.getBody();
		Assert.assertEquals("Search Guard index not initialized (SG11)", errorString);
		
		// PUT request
		response = rh.executePutRequest("/_searchguard/api/actiongroup/READ", FileHelper.loadFile("actiongroup_read.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		
		// DELETE request
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		
		// setup index now
		initialize(this.clusterInfo);
		
		// GET configuration
		response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals("CLUSTER_ALL", settings.get("sg_admin.cluster.0"));

	}
}
