/*
 * (C) Copyright IBM Corp. 2017, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.server.test;

import static com.ibm.fhir.model.test.TestUtil.isResourceInResponse;
import static com.ibm.fhir.model.type.Code.code;
import static com.ibm.fhir.model.type.Uri.uri;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.testng.annotations.Test;

import com.ibm.fhir.client.FHIRParameters;
import com.ibm.fhir.client.FHIRRequestHeader;
import com.ibm.fhir.client.FHIRResponse;
import com.ibm.fhir.core.FHIRMediaType;
import com.ibm.fhir.model.format.Format;
import com.ibm.fhir.model.generator.FHIRGenerator;
import com.ibm.fhir.model.generator.exception.FHIRGeneratorException;
import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Bundle.Entry;
import com.ibm.fhir.model.resource.Bundle.Link;
import com.ibm.fhir.model.resource.Condition;
import com.ibm.fhir.model.resource.Observation;
import com.ibm.fhir.model.resource.OperationOutcome;
import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.test.TestUtil;
import com.ibm.fhir.model.type.Canonical;
import com.ibm.fhir.model.type.Coding;
import com.ibm.fhir.model.type.Instant;
import com.ibm.fhir.model.type.Meta;

public class SearchAllTest extends FHIRServerTestBase {

    private static final boolean DEBUG_SEARCH = false;

    private String patientId, patientId2, observationId;
    private Instant lastUpdated;
    private Patient patient4DuplicationTest = null;
    private String strUniqueTag = UUID.randomUUID().toString();
    // By default, the tests runs on the default data store of the default tenant, can be changed to test
    // other data store or other tenant.
    private final String tenantName = "default";
    private final String dataStoreId = "default";

    private final FHIRRequestHeader headerTenant =
            new FHIRRequestHeader("X-FHIR-TENANT-ID", tenantName);
    private final FHIRRequestHeader headerDataStore =
            new FHIRRequestHeader("X-FHIR-DSID", dataStoreId);

    @Test(groups = { "server-search-all" })
    public void testCreatePatient() throws Exception {
        WebTarget target = getWebTarget();

        // Build a new Patient with 2 tags and one duplicated tag and then call the 'create' API.
        Patient patient = TestUtil.readLocalResource("Patient_JohnDoe.json");

        Coding security = Coding.builder().system(uri("http://ibm.com/fhir/security")).code(code("security")).build();
        Coding tag = Coding.builder().system(uri("http://ibm.com/fhir/tag")).code(code("tag")).build();
        Coding tag2 = Coding.builder().system(uri("http://ibm.com/fhir/tag")).code(code("tag2")).build();

        patient =
                patient.toBuilder()
                        .meta(Meta.builder()
                                .security(security)
                                .tag(tag)
                                .tag(tag)
                                .tag(tag2)
                                .profile(Canonical.of("http://ibm.com/fhir/profile/Profile"))
                                .build())
                        .build();

        if (DEBUG_SEARCH) {
            generateOutput(patient);
        }

        Entity<Patient> entity = Entity.entity(patient, FHIRMediaType.APPLICATION_FHIR_JSON);
        Response response = target.path("Patient").request()
                .header("X-FHIR-TENANT-ID", tenantName)
                .header("X-FHIR-DSID", dataStoreId)
                .post(entity, Response.class);
        assertResponse(response, Response.Status.CREATED.getStatusCode());

        // Get the patient's logical id value.
        patientId = getLocationLogicalId(response);

        // Next, call the 'read' API to retrieve the new patient and verify it.
        response  = target.path("Patient/" + patientId).request(FHIRMediaType.APPLICATION_FHIR_JSON)
                .header("X-FHIR-TENANT-ID", tenantName)
                .header("X-FHIR-DSID", dataStoreId)
                .get();
        assertResponse(response, Response.Status.OK.getStatusCode());
        patient4DuplicationTest = response.readEntity(Patient.class);
        TestUtil.assertResourceEquals(patient, patient4DuplicationTest);

        lastUpdated = patient4DuplicationTest.getMeta().getLastUpdated();
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testCreateObservation() throws Exception {
        WebTarget target = getWebTarget();

        Observation observation =
                TestUtil.buildPatientObservation(patientId, "Observation1.json");

        Entity<Observation> entity =
                Entity.entity(observation, FHIRMediaType.APPLICATION_FHIR_JSON);
        Response response =
                target.path("Observation").request()
                        .header("X-FHIR-TENANT-ID", tenantName)
                        .header("X-FHIR-DSID", dataStoreId)
                        .post(entity, Response.class);
        assertResponse(response, Response.Status.CREATED.getStatusCode());

        // Get the patient's logical id value.
        observationId = getLocationLogicalId(response);

        // Next, call the 'read' API to retrieve the new observation and verify it.
        response  = target.path("Observation/" + observationId).request(FHIRMediaType.APPLICATION_FHIR_JSON)
                .header("X-FHIR-TENANT-ID", tenantName)
                .header("X-FHIR-DSID", dataStoreId)
                .get();
        assertResponse(response, Response.Status.OK.getStatusCode());
        Observation createdObservation = response.readEntity(Observation.class);
        TestUtil.assertResourceEquals(observation, createdObservation);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingId() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_id", patientId);
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingLastUpdated() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_lastUpdated", lastUpdated.getValue().toString());

        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingLastUpdatedGeLe() throws Exception {
        // ge2018-09-01T00:00:00Z&
        // le2018-09-01T00:00:00Z&
        FHIRParameters parameters = new FHIRParameters();
        ZoneId zoneId = ZoneId.from(lastUpdated.getValue());
        ZonedDateTime beforeOneHourZdt = ZonedDateTime.ofInstant(lastUpdated.getValue().toInstant().minus(1, ChronoUnit.HOURS),zoneId);
        ZonedDateTime afterOneHourZdt = ZonedDateTime.ofInstant(lastUpdated.getValue().toInstant().plus(1, ChronoUnit.HOURS),zoneId);
        parameters.searchParam("_lastUpdated", "ge" + beforeOneHourZdt.toString());
        parameters.searchParam("_lastUpdated", "le" + afterOneHourZdt.toString());

        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingLastUpdatedMultipleGeLe() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        ZoneId zoneId = ZoneId.from(lastUpdated.getValue());
        ZonedDateTime beforeOneHourZdt = ZonedDateTime.ofInstant(lastUpdated.getValue().toInstant().minus(1, ChronoUnit.HOURS),zoneId);
        ZonedDateTime afterOneHourZdt = ZonedDateTime.ofInstant(lastUpdated.getValue().toInstant().plus(1, ChronoUnit.HOURS),zoneId);
        parameters.searchParam("_lastUpdated", "ge" + beforeOneHourZdt.toString() + ",ge2018");
        parameters.searchParam("_lastUpdated", "le" + afterOneHourZdt.toString() + ",le2029");

        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingLastUpdatedMultipleGeLeOneInvalidEach() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        ZoneId zoneId = ZoneId.from(lastUpdated.getValue());
        ZonedDateTime beforeOneHourZdt = ZonedDateTime.ofInstant(lastUpdated.getValue().toInstant().minus(1, ChronoUnit.HOURS),zoneId);
        ZonedDateTime afterOneHourZdt = ZonedDateTime.ofInstant(lastUpdated.getValue().toInstant().plus(1, ChronoUnit.HOURS),zoneId);
        parameters.searchParam("_lastUpdated", "ge" + beforeOneHourZdt.toString() + ",ge2029");
        parameters.searchParam("_lastUpdated", "le" + afterOneHourZdt.toString() + ",le2018");

        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingTag() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", "tag");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);

        /*
         * "expression" : "Resource.meta.tag", <br/> "xpath" : "f:Resource/f:meta/f:tag",
         */
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingSecurity() throws Exception {
        // <expression value="Resource.meta.security"/>
        // <xpath value="f:Resource/f:meta/f:security"/>

        FHIRParameters parameters = new FHIRParameters();

        // Original - "http://ibm.com/fhir/security|security"
        parameters.searchParam("_security", "security");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);

        assertNotNull(bundle);
        if (DEBUG_SEARCH) {
            generateOutput(bundle);
        }

        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingProfile() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_profile", "http://ibm.com/fhir/profile/Profile");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreateObservation" })
    public void testSearchAllWithTagMissing_Results() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag:missing", "true");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreateObservation" })
    public void testSearchAllWithTagMissing_NoResults() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_id", observationId);
        parameters.searchParam("_tag:missing", "false");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().isEmpty());
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllWithTagNotMissing_Results() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_id", patientId);
        parameters.searchParam("_tag:missing", "false");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertEquals(bundle.getEntry().size(), 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllWithTagNotMissing_NoResults() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_id", patientId);
        parameters.searchParam("_tag:missing", "true");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().isEmpty());
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreateObservation" })
    public void testSearchAllChainWithTagNotMissing_Results() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_type", "Observation");
        parameters.searchParam("_id", observationId);
        parameters.searchParam("subject:Patient._tag:missing", "false");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertEquals(bundle.getEntry().size(), 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreateObservation" })
    public void testSearchAllChainWithTagMissing_NoResults() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_type", "Observation");
        parameters.searchParam("_id", observationId);
        parameters.searchParam("subject:Patient._tag:missing", "true");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().isEmpty());
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreateObservation" })
    public void testSearchAllWithTagNot_Results() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag:not", "tag3");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreateObservation" })
    public void testSearchAllWithTagNot_NoResults() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_id", patientId);
        parameters.searchParam("_tag:not", "tag2");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().isEmpty());
    }

    /*
     * generates the output into a resource.
     */
    public static void generateOutput(Resource resource) {

        try (StringWriter writer = new StringWriter();) {
            FHIRGenerator.generator(Format.JSON, true).generate(resource, System.out);
            System.out.println(writer.toString());
        } catch (FHIRGeneratorException e) {

            e.printStackTrace();
            fail("unable to generate the fhir resource to JSON");

        } catch (IOException e1) {
            e1.printStackTrace();
            fail("unable to generate the fhir resource to JSON (io problem) ");
        }

    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsing2TagsAndNoExistingTag() throws Exception {
        int firstRunNumber;
        FHIRParameters parameters = new FHIRParameters();
        // tag88 doesn't exist, this case is created according to a reported test failure.
        parameters.searchParam("_tag", "http://ibm.com/fhir/tag|tag88,tag2,tag");
        parameters.searchParam("_count", "1000");
        parameters.searchParam("_page", "1");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);

        firstRunNumber = bundle.getEntry().size();
        assertTrue(firstRunNumber >= 1);
        // Create one more patient with 2 tags: "tag" and "tag2".
        testCreatePatient();
        response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        bundle = response.getResource(Bundle.class);
        // The second run should only have one more new record found.
        // Because we limit the page size to 1000, so we only do this check when firstRunNumber < 1000.
        if (firstRunNumber < 1000) {
            assertTrue(bundle.getEntry().size() == firstRunNumber + 1);
            List<Resource> lstRes = new ArrayList<Resource>();
            for (Bundle.Entry entry : bundle.getEntry()) {
                lstRes.add(entry.getResource());
            }
            assertTrue(isResourceInResponse(patient4DuplicationTest, lstRes));
        } else {
            // Just in case there are more than 1000 matches, then simply verify that there is
            // no duplicated resource in the search results, Just need to do the verification for the second run.
            HashSet<String> resourceIdSet = new HashSet<String>();
            for (Entry entry : bundle.getEntry()) {
                resourceIdSet.add(entry.getResource().getClass().getSimpleName()
                        + ":" + entry.getResource().getId());
            }
            assertTrue(bundle.getEntry().size() == resourceIdSet.size());
        }
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsing2Tags() throws Exception {
        int firstRunNumber;
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", "http://ibm.com/fhir/tag|tag2,tag");
        parameters.searchParam("_count", "1001");
        parameters.searchParam("_page", "1");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        // Check that count is set to the maxPageSize (1000) for the tenant
        String selfLink = getSelfLink(bundle);
        assertTrue(selfLink.contains("_count=1000"));

        firstRunNumber = bundle.getEntry().size();
        assertTrue(firstRunNumber >= 1);
        // create one more patient with 2 tags: "tag" and "tag2".
        testCreatePatient();
        response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        bundle = response.getResource(Bundle.class);
        // The second run should only have one more new record found.
        // Because we limit the page size to 1000, so we only do this check when firstRunNumber < 1000.
        if (firstRunNumber < 1000) {
            assertTrue(bundle.getEntry().size() == firstRunNumber + 1);
            List<Resource> lstRes = new ArrayList<Resource>();
            for (Bundle.Entry entry : bundle.getEntry()) {
                lstRes.add(entry.getResource());
            }
            assertTrue(isResourceInResponse(patient4DuplicationTest, lstRes));
        } else {
            // Just in case there are more than 1000 matches, then simply verify that there is
            // no duplicated resource in the search results, Just need to do the verification for the second run.
            HashSet<String> resourceIdSet = new HashSet<String>();
            for (Entry entry : bundle.getEntry()) {
                resourceIdSet.add(entry.getResource().getClass().getSimpleName()
                        + ":" + entry.getResource().getId());
            }
            assertTrue(bundle.getEntry().size() == resourceIdSet.size());
        }
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsing2FullTags() throws Exception {
        int firstRunNumber;
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", "http://ibm.com/fhir/tag|tag2,http://ibm.com/fhir/tag|tag");
        parameters.searchParam("_count", "1000");
        parameters.searchParam("_page", "1");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);

        firstRunNumber = bundle.getEntry().size();
        assertTrue(firstRunNumber >= 1);
        // Create one more patient with 2 tags: "tag" and "tag2".
        testCreatePatient();
        response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        bundle = response.getResource(Bundle.class);
        // The second run should only have one more new record found.
        // Because we limit the page size to 1000, so we only do this check when firstRunNumber < 1000.
        if (firstRunNumber < 1000) {
            assertTrue(bundle.getEntry().size() == firstRunNumber + 1);
            List<Resource> lstRes = new ArrayList<Resource>();
            for (Bundle.Entry entry : bundle.getEntry()) {
                lstRes.add(entry.getResource());
            }
            assertTrue(isResourceInResponse(patient4DuplicationTest, lstRes));
        } else {
            // Just in case there are more than 1000 matches, then simply verify that there is
            // no duplicated resource in the search results, Just need to do the verification for the second run.
            HashSet<String> resourceIdSet = new HashSet<String>();
            for (Entry entry : bundle.getEntry()) {
                resourceIdSet.add(entry.getResource().getClass().getSimpleName()
                        + ":" + entry.getResource().getId());
            }
            assertTrue(bundle.getEntry().size() == resourceIdSet.size());
        }
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatient" })
    public void testSearchAllUsingOneTag() throws Exception {
        int firstRunNumber;
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", "tag");
        parameters.searchParam("_count", "1000");
        parameters.searchParam("_page", "1");
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);

        firstRunNumber = bundle.getEntry().size();
        assertTrue(firstRunNumber >= 1);
        // Create one more patient with 2 tags: "tag" and "tag2".
        testCreatePatient();
        response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        bundle = response.getResource(Bundle.class);
        // The second run should only have one more new record found.
        // Because we limit the page size to 1000, so we only do this check when firstRunNumber < 1000.
        if (firstRunNumber < 1000) {
            assertTrue(bundle.getEntry().size() == firstRunNumber + 1);
            List<Resource> lstRes = new ArrayList<Resource>();
            for (Bundle.Entry entry : bundle.getEntry()) {
                lstRes.add(entry.getResource());
            }
            assertTrue(isResourceInResponse(patient4DuplicationTest, lstRes));
        } else {
            // Just in case there are more than 1000 matches, then simply verify that there is
            // no duplicated resource in the search results, Just need to do the verification for the second run.
            HashSet<String> resourceIdSet = new HashSet<String>();
            for (Entry entry : bundle.getEntry()) {
                resourceIdSet.add(entry.getResource().getClass().getSimpleName()
                        + ":" + entry.getResource().getId());
            }
            assertTrue(bundle.getEntry().size() == resourceIdSet.size());
        }
    }

    @Test(groups = { "server-search-all" })
    public void testCreatePatientAndObservationWithUniqueTag() throws Exception {
        Coding uniqueTag = Coding.builder().system(uri("http://ibm.com/fhir/tag")).code(code(strUniqueTag)).build();
        WebTarget target = getWebTarget();

        // Create a new Patient with the unique tag.
        Patient patient = TestUtil.readLocalResource("Patient_JohnDoe.json");
        Coding security = Coding.builder().system(uri("http://ibm.com/fhir/security")).code(code("security")).build();

        patient =
                patient.toBuilder()
                        .meta(Meta.builder()
                                .security(security)
                                .tag(uniqueTag)
                                .profile(Canonical.of("http://ibm.com/fhir/profile/Profile"))
                                .build())
                        .build();

        Entity<Patient> entity = Entity.entity(patient, FHIRMediaType.APPLICATION_FHIR_JSON);
        Response response = target.path("Patient").request()
                .header("X-FHIR-TENANT-ID", tenantName)
                .header("X-FHIR-DSID", dataStoreId)
                .post(entity, Response.class);
        assertResponse(response, Response.Status.CREATED.getStatusCode());

        // Get the patient's logical id value.
        patientId2 = getLocationLogicalId(response);

        // Create a new observation with the unique tag for the above patient.
        Observation observation =
                TestUtil.buildPatientObservation(patientId2, "Observation1.json");
        observation =
                observation.toBuilder()
                        .meta(Meta.builder()
                                .tag(uniqueTag)
                                .build())
                        .build();

        Entity<Observation> entity2 =
                Entity.entity(observation, FHIRMediaType.APPLICATION_FHIR_JSON);
        Response response2 =
                target.path("Observation").request()
                        .header("X-FHIR-TENANT-ID", tenantName)
                        .header("X-FHIR-DSID", dataStoreId)
                        .post(entity2, Response.class);
        assertResponse(response2, Response.Status.CREATED.getStatusCode());

        // Create a Condition with subject points to the created patient.
        Condition condition = buildCondition(patientId2, "Condition.json");
        Entity<Condition> obs = Entity.entity(condition, FHIRMediaType.APPLICATION_FHIR_JSON);
        response = target.path("Condition").request()
                .header("X-FHIR-TENANT-ID", tenantName)
                .header("X-FHIR-DSID", dataStoreId)
                .post(obs, Response.class);
        assertResponse(response, Response.Status.CREATED.getStatusCode());

    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2UsingUniqueTag() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", strUniqueTag);
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() == 2);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2UsingUniqueTag_OneType() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", strUniqueTag);
        parameters.searchParam("_type", "Patient");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() == 1);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2UsingUniqueTag_TwoTypes() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", strUniqueTag);
        parameters.searchParam("_type", "Patient,Observation");
        parameters.searchParam("_sort", "_lastUpdated");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() == 2);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2_TwoTypes_ChainedParameter() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("subject:Patient._tag", strUniqueTag);
        parameters.searchParam("_type", "Observation,Condition");
        parameters.searchParam("_sort", "_id");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assert (bundle.getEntry().size() == 2);
        // verify self link in the response bundle
        assertTrue(bundle.getLink().size() == 1);
        String selfLink = getSelfLink(bundle);
        assertTrue(selfLink.contains("subject:Patient._tag"));
        // Check that count is set to defaultPageSize (10) for the tenant
        assertTrue(selfLink.contains("_count=10"));
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2_TwoTypes_InvalidChainedParameter() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("subject:Practitioner.name", "John");
        parameters.searchParam("_type", "Account,Observation");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.BAD_REQUEST.getStatusCode());
        assertExceptionOperationOutcome(response.getResponse().readEntity(OperationOutcome.class),
                "Modifier resource type [Practitioner] is not allowed for search parameter [subject] of resource type [Observation]");
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2UsingUniqueTag_TwoTypes_Summary() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", strUniqueTag);
        parameters.searchParam("_type", "Patient,Observation");
        parameters.searchParam("_sort", "_lastUpdated");
        parameters.searchParam("_summary", "true");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() == 2);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2UsingUniqueTag_TwoTypes_elements() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("_tag", strUniqueTag);
        parameters.searchParam("_type", "Patient,Observation");
        parameters.searchParam("_sort", "_lastUpdated");
        parameters.searchParam("_elements", "id");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() == 2);
    }

    @Test(groups = { "server-search-all" }, dependsOnMethods = { "testCreatePatientAndObservationWithUniqueTag" })
    public void testSearchAll2_TwoTypes_InvalidInclude() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        parameters.searchParam("subject:Practitioner.name", "John");
        parameters.searchParam("_type", "Account,Observation");
        parameters.searchParam("_include", "Observation:subject");
        FHIRResponse response = client.searchAll(parameters, true, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.BAD_REQUEST.getStatusCode());
        assertExceptionOperationOutcome(response.getResponse().readEntity(OperationOutcome.class),
                "system search not supported with _include or _revinclude");
    }

    @Test(groups = { "server-search-all" })
    public void testSearchAllUrlReflexsivityUsingLastUpdated() throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        String lastUpdated = "ge2000";
        parameters.searchParam("_lastUpdated", lastUpdated);
        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);
        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
        List<Link> links = bundle.getLink();

        /*
         * Runs through the links and checks for self and rel.
         * It subsequently connects to the self to verify it's 200.
         */
        boolean validSelf = false;
        boolean validRel = false;
        for (Link link : links) {
            String type = link.getRelation().getValue();
            String uri = link.getUrl().getValue();
            if ("self".equals(type)) {
                verifyReflexsiveUrl(uri, lastUpdated);
                validSelf = true;
            } else if ("next".equals(type)) {
                verifyReflexsiveUrl(uri, lastUpdated);
                validRel = true;
            }
        }

        assertTrue(validSelf);
        assertTrue(validRel);

        /*
         * Runs through the fullUrl of the entries and checks for appropriate values
         */
        for (Entry entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            // The client always ensures that the baseUrl ends with a '/'
            String expectedBase = client.getWebTarget().getUri() + resource.getClass().getSimpleName();
            assertTrue(entry.getFullUrl().getValue().startsWith(expectedBase),
                    "fullUrl " + entry.getFullUrl().getValue() + " should start with " + expectedBase);
        }
    }

    /*
     * Queries based on the URI the endpoint with the query parameter and value.
     */
    private void verifyReflexsiveUrl(String uri, String expectedLastUpdated) throws Exception {
        FHIRParameters parameters = new FHIRParameters();
        WebTarget target = client.getWebTarget();
        String queryParameterStrings = uri.replaceAll(target.getUri().toString() + "_search\\?", "");
        String[] queryParams = queryParameterStrings.split("&");
        String actualLastUpdated = null;
        for (String queryParam : queryParams) {
            String name = queryParam.split("=")[0];
            String value = queryParam.split("=")[1];
            if ("_lastUpdated".equals(name)) {
                actualLastUpdated = value;
            }
            parameters.searchParam(name, value);
        }
        assertEquals(actualLastUpdated, expectedLastUpdated);

        FHIRResponse response = client.searchAll(parameters, false, headerTenant, headerDataStore);

        assertResponse(response.getResponse(), Response.Status.OK.getStatusCode());
        Bundle bundle = response.getResource(Bundle.class);
        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }
}