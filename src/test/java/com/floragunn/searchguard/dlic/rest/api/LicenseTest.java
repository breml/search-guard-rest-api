/*
 * Copyright 2016 by floragunn GmbH - All rights reserved
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

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.configuration.SearchGuardLicense;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class LicenseTest extends AbstractRestApiUnitTest {

	private final static String CONFIG_LICENSE_KEY = "searchguard.dynamic.license";
	private final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private Map<String, String> originalConfig;
	
	// start dates
	private LocalDate expiredStartDate = LocalDate.of(2016, Month.JANUARY, 31);
	private LocalDate validStartDate = LocalDate.of(2017, Month.JANUARY, 31);
	private LocalDate notStartedStartDate = LocalDate.of(2116, Month.JANUARY, 31);

	// expiry dates
	private LocalDate expiredExpiryDate = validStartDate.plusYears(1);
	private LocalDate validExpiryDate = validStartDate.plusYears(100);
	private LocalDate notStartedExpiryDate = notStartedStartDate.plusYears(1);
	private LocalDate trialExpiryDate = LocalDate.now().plusDays(91);
	
	@Test
	public void testLicenseApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;
		
		// get sg_config at the beginning of the test. Subsequent calls must not alter
		// the contents of the config in any way, other than setting the license string.
		// Store conent as map for further user
		originalConfig = getCurrentConfig();
		
		Map<String, String> currentConfig;
		
		// check license exists - has to be trial license
		// Map<String, String> settingsAsMap = getCurrentLicense()
		// Assert.assertEquals(SearchGuardLicense.Type.TRIAL.name(), settingsAsMap.get("sg_license.type"));
		// Assert.assertEquals("unlimited", settingsAsMap.get("sg_license.allowed_node_count_per_cluster"));
		// Assert.assertEquals("true", settingsAsMap.get("sg_license.is_valid"));
		// Assert.assertEquals(issueDate.format(formatter), settingsAsMap.get("sg_license.start_date"));
		// Assert.assertEquals(expiryDate.format(formatter), settingsAsMap.get("sg_license.expiry_date"));

		// upload new licenses - all valid forever
		uploadAndCheckValidLicense("full_valid_forever.txt", HttpStatus.SC_CREATED); // first license upload, hence 201 return code
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);

		uploadAndCheckValidLicense("sme_valid_forever.txt");
		checkCurrentLicenseProperties(SearchGuardLicense.Type.SME, Boolean.TRUE, "5", validStartDate, validExpiryDate);

		uploadAndCheckValidLicense("oem_valid_forever.txt");
		checkCurrentLicenseProperties(SearchGuardLicense.Type.OEM, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);

		uploadAndCheckValidLicense("academic_valid_forever.txt");
		checkCurrentLicenseProperties(SearchGuardLicense.Type.ACADEMIC, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);
		
		// invalid - we run 3 nodes, but license has only one node allowed
		Map<String, String> result = uploadAndCheckInvalidLicense("single_valid_forever.txt", HttpStatus.SC_BAD_REQUEST);
		// Make sure license was rejected and old values are still in place
		checkCurrentLicenseProperties(SearchGuardLicense.Type.ACADEMIC, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);
		Assert.assertEquals("License invalid due to: Only 1 node(s) allowed but you run 3 node(s)", result.get("message"));
		
		// license not started - deemed valid, we don't issue licenses in advance
		uploadAndCheckValidLicense("full_not_started.txt");
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", notStartedStartDate, notStartedExpiryDate);

		// license expired
		result = uploadAndCheckInvalidLicense("sme_expired.txt", HttpStatus.SC_BAD_REQUEST);
		// Make sure license was rejected and old values are still in place
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", notStartedStartDate, notStartedExpiryDate);
		Assert.assertEquals("License invalid due to: License is expired", result.get("message"));
		
		// Error cases. In each case, previous license the following uploaded license needs to stay intact:
		// upload new licenses - all valid forever
		uploadAndCheckValidLicense("full_valid_forever.txt"); // first license upload, hence 201 return code
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);

		
		// no JSON payload
		HttpResponse response = rh.executePutRequest("/_searchguard/api/license", null, new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		String msg = settings.get("reason");
		Assert.assertEquals("Request body required for this action.", msg);
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);
		
		// missing license in JSON payload
		response = rh.executePutRequest("/_searchguard/api/license", "{ \"sg_license\": \"\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		msg = settings.get("message");
		Assert.assertEquals("License must not be null.", msg);
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);
		
		// no no body
		response = rh.executePutRequest("/_searchguard/api/license", "{ }", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		msg = settings.get("reason");
		Assert.assertEquals("Request body required for this action.", msg);
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);
		
		// invalid, unparseable license in JSON payload
		response = rh.executePutRequest("/_searchguard/api/license", "{ \"sg_license\": \"lalala\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		msg = settings.get("message");
		Assert.assertEquals("License could not be decoded due to: org.bouncycastle.openpgp.PGPException: Cannot find license signature", msg);		
		checkCurrentLicenseProperties(SearchGuardLicense.Type.FULL, Boolean.TRUE, "unlimited", validStartDate, validExpiryDate);
	}

	private final String createLicenseRequestBody(String licenseString) throws Exception {
		return "{ \"sg_license\": \"" + licenseString + "\"}";
	}

	private final String loadLicenseKey(String filename) throws Exception {
		return FileHelper.loadFile("license/" + filename);
	}
	
	private final Map<String, String> checkCurrentLicenseProperties(SearchGuardLicense.Type type, Boolean isValid, String nodeCount,LocalDate startDate, LocalDate expiryDate ) throws Exception {
		 Map<String, String> settingsAsMap = getCurrentLicense();
		 Assert.assertEquals(type.name(), settingsAsMap.get("sg_license.type"));
		 Assert.assertEquals(nodeCount, settingsAsMap.get("sg_license.allowed_node_count_per_cluster"));
		 Assert.assertEquals(isValid.toString(), settingsAsMap.get("sg_license.is_valid"));
		 Assert.assertEquals(startDate.format(formatter), settingsAsMap.get("sg_license.start_date"));
		 Assert.assertEquals(expiryDate.format(formatter), settingsAsMap.get("sg_license.expiry_date"));
		 return settingsAsMap;
	}
		
	private final void uploadAndCheckValidLicense(String licenseFileName) throws Exception {
	 uploadAndCheckValidLicense(licenseFileName, HttpStatus.SC_OK);
	}

	private final Map<String, String> uploadAndCheckInvalidLicense(String licenseFileName, int statusCode) throws Exception {
		String licenseKey = loadLicenseKey(licenseFileName);
		HttpResponse response = rh.executePutRequest("/_searchguard/api/license", createLicenseRequestBody(licenseKey), new Header[0]);
		Assert.assertEquals(statusCode, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		return settings.getAsMap();
	}

	private final void uploadAndCheckValidLicense(String licenseFileName, int statusCode) throws Exception {
		String licenseKey = loadLicenseKey(licenseFileName);
		HttpResponse response = rh.executePutRequest("/_searchguard/api/license", createLicenseRequestBody(licenseKey), new Header[0]);
		Assert.assertEquals(statusCode, response.getStatusCode());
		Map<String, String> config = getCurrentConfig();
		Map<String, String> expectectConfig = new HashMap<String, String>(originalConfig);
		expectectConfig.put(CONFIG_LICENSE_KEY, licenseKey);
		// Old config + license must equal newly stored config
		Assert.assertEquals(expectectConfig, config); 
	}
	
	private final Map<String, String> getCurrentLicense() throws Exception {
		HttpResponse response = rh.executeGetRequest("_searchguard/api/license");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		return settings.getAsMap();
	}

	private final Map<String, String> getCurrentConfig() throws Exception {
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/config");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		// sanity
		Assert.assertEquals(settings.getAsBoolean("searchguard.dynamic.authc.authentication_domain_basic_internal.enabled", false), true);
		return settings.getAsMap();
	}
}
