/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.server.test;

import static com.ibm.fhir.model.type.String.string;
import static com.ibm.fhir.path.evaluator.FHIRPathEvaluator.SINGLETON_TRUE;
import static com.ibm.fhir.path.util.FHIRPathUtil.empty;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ibm.fhir.cache.CacheManager;
import com.ibm.fhir.model.resource.Observation;
import com.ibm.fhir.model.resource.OperationOutcome;
import com.ibm.fhir.model.resource.OperationOutcome.Builder;
import com.ibm.fhir.model.resource.Organization;
import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.type.CodeableConcept;
import com.ibm.fhir.model.type.HumanName;
import com.ibm.fhir.model.type.Id;
import com.ibm.fhir.model.type.Instant;
import com.ibm.fhir.model.type.Meta;
import com.ibm.fhir.model.type.Reference;
import com.ibm.fhir.model.type.code.ObservationStatus;
import com.ibm.fhir.path.FHIRPathBooleanValue;
import com.ibm.fhir.path.FHIRPathNode;
import com.ibm.fhir.path.evaluator.FHIRPathEvaluator;
import com.ibm.fhir.path.evaluator.FHIRPathEvaluator.EvaluationContext;
import com.ibm.fhir.path.function.ResolveFunction;
import com.ibm.fhir.path.function.registry.FHIRPathFunctionRegistry;
import com.ibm.fhir.persistence.FHIRPersistence;
import com.ibm.fhir.persistence.FHIRPersistenceTransaction;
import com.ibm.fhir.persistence.MultiResourceResult;
import com.ibm.fhir.persistence.ResourceChangeLogRecord;
import com.ibm.fhir.persistence.ResourcePayload;
import com.ibm.fhir.persistence.SingleResourceResult;
import com.ibm.fhir.persistence.context.FHIRPersistenceContext;
import com.ibm.fhir.persistence.context.FHIRPersistenceContextFactory;
import com.ibm.fhir.persistence.exception.FHIRPersistenceException;
import com.ibm.fhir.persistence.helper.PersistenceHelper;
import com.ibm.fhir.server.resolve.ServerResolveFunction;

public class ServerResolveFunctionTest {
    private PersistenceHelper persistenceHelper;

    @BeforeClass
    public void beforeClass() {
        persistenceHelper = new PersistenceHelperImpl();
        FHIRPathFunctionRegistry.getInstance().register(new ServerResolveFunction(persistenceHelper) {
            @Override
            protected boolean matchesServiceBaseUrl(String baseUrl) {
                return "https://localhost:9443/fhir-server/api/v4/".equals(baseUrl);
            }
        });
    }

    @Test
    public void testCreatePatient() throws Exception {
        Patient patient = Patient.builder()
                .id("12345")
                .name(HumanName.builder()
                    .family(string("Doe"))
                    .given(string("John"))
                    .build())
                .deceased(com.ibm.fhir.model.type.Boolean.FALSE)
                .managingOrganization(Reference.builder()
                    .reference(string("Organization/67890"))
                    .build())
                .build();

        FHIRPersistence persistence = persistenceHelper.getFHIRPersistenceImplementation();
        FHIRPersistenceContext context = FHIRPersistenceContextFactory.createPersistenceContext(null);

        SingleResourceResult<? extends Resource> result = persistence.update(context, "12345", patient);
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());

        result = persistence.read(context, Patient.class, "12345");
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());
    }

    @Test
    public void testCreateObservation() throws Exception {
        Observation observation = Observation.builder()
                .id("54321")
                .status(ObservationStatus.FINAL)
                .code(CodeableConcept.builder()
                    .text(string("test"))
                    .build())
                .subject(Reference.builder()
                    .reference(string("Patient/12345"))
                    .build())
                .build();

        FHIRPersistence persistence = persistenceHelper.getFHIRPersistenceImplementation();
        FHIRPersistenceContext context = FHIRPersistenceContextFactory.createPersistenceContext(null);
        SingleResourceResult<? extends Resource> result = persistence.update(context, "54321", observation);
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());

        result = persistence.read(context, Observation.class, "54321");
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());
    }

    @Test
    public void testCreateOrganization() throws Exception {
        Organization organization = Organization.builder()
                .id("67890")
                .name(string("Good Samaritan"))
                .build();

        FHIRPersistence persistence = persistenceHelper.getFHIRPersistenceImplementation();
        FHIRPersistenceContext context = FHIRPersistenceContextFactory.createPersistenceContext(null);
        SingleResourceResult<? extends Resource> result = persistence.update(context, "67890", organization);
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());

        result = persistence.read(context, Organization.class, "67890");
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());
    }

    @Test(dependsOnMethods = { "testCreatePatient", "testCreateObservation", "testCreateOrganization" })
    public void testServerResolveFunction() throws Exception {
        FHIRPersistence persistence = persistenceHelper.getFHIRPersistenceImplementation();
        FHIRPersistenceContext context = FHIRPersistenceContextFactory.createPersistenceContext(null);

        SingleResourceResult<Observation> result = persistence.read(context, Observation.class, "54321");
        assertNotNull(result);
        assertNotNull(result.getResource());
        assertTrue(result.isSuccess());

        Observation observation = result.getResource();

        FHIRPathEvaluator evaluator = FHIRPathEvaluator.evaluator();
        EvaluationContext evaluationContext = new EvaluationContext(observation);

        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.TRUE);
        Collection<FHIRPathNode> nodes = evaluator.evaluate(evaluationContext, "subject.resolve() is Patient");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().id = '12345'");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().name.given = 'John'");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().name.family = 'Doe'");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().deceased = false");
        assertEquals(nodes, SINGLETON_TRUE);

        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.FALSE);
        nodes = evaluator.evaluate(evaluationContext, "subject.resolve() is Patient");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().id = '12345'");
        assertEquals(nodes, empty());

        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.TRUE);
        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().managingOrganization.resolve() is Organization");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().managingOrganization.resolve().id = '67890'");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().managingOrganization.resolve().name = 'Good Samaritan'");
        assertEquals(nodes, SINGLETON_TRUE);

        // relative reference with version
        CacheManager.invalidateAll(ServerResolveFunction.RESOURCE_CACHE_NAME);
        observation = observation.toBuilder()
                .subject(Reference.builder()
                    .reference(string("Patient/12345/_history/1"))
                    .build())
                .build();
        evaluationContext = new EvaluationContext(observation);
        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.TRUE);
        nodes = evaluator.evaluate(evaluationContext, "subject.resolve() is Patient");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().id = '12345'");
        assertEquals(nodes, SINGLETON_TRUE);

        // relative reference with version (negative case)
        CacheManager.invalidateAll(ServerResolveFunction.RESOURCE_CACHE_NAME);
        observation = observation.toBuilder()
                .subject(Reference.builder()
                    .reference(string("Patient/12345/_history/2"))
                    .build())
                .build();
        evaluationContext = new EvaluationContext(observation);
        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.TRUE);
        nodes = evaluator.evaluate(evaluationContext, "subject.resolve() is Patient");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().id = '12345'");
        assertEquals(nodes, empty());

        // absolute reference
        CacheManager.invalidateAll(ServerResolveFunction.RESOURCE_CACHE_NAME);
        observation = observation.toBuilder()
                .subject(Reference.builder()
                    .reference(string("https://localhost:9443/fhir-server/api/v4/Patient/12345"))
                    .build())
                .build();
        evaluationContext = new EvaluationContext(observation);
        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.TRUE);
        nodes = evaluator.evaluate(evaluationContext, "subject.resolve() is Patient");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().id = '12345'");
        assertEquals(nodes, SINGLETON_TRUE);

        // absolute reference (negative case)
        CacheManager.invalidateAll(ServerResolveFunction.RESOURCE_CACHE_NAME);
        observation = observation.toBuilder()
                .subject(Reference.builder()
                    .reference(string("http://ibm.com/fhir-server/api/v4/Patient/12345"))
                    .build())
                .build();
        evaluationContext = new EvaluationContext(observation);
        evaluationContext.setExternalConstant(ResolveFunction.RESOLVE_RELATIVE_REFERENCES, FHIRPathBooleanValue.TRUE);
        nodes = evaluator.evaluate(evaluationContext, "subject.resolve() is Patient");
        assertEquals(nodes, SINGLETON_TRUE);

        nodes = evaluator.evaluate(evaluationContext, "subject.resolve().id = '12345'");
        assertEquals(nodes, empty());
    }

    public static class PersistenceHelperImpl implements PersistenceHelper {
        private final FHIRPersistence persistence;

        public PersistenceHelperImpl() {
            persistence = new PersistenceImpl();
        }

        @Override
        public FHIRPersistence getFHIRPersistenceImplementation() throws FHIRPersistenceException {
            return persistence;
        }

        @Override
        public FHIRPersistence getFHIRPersistenceImplementation(String factoryPropertyName) throws FHIRPersistenceException {
            throw new UnsupportedOperationException();
        }
    }

    public static class PersistenceImpl implements FHIRPersistence {
        private final Map<Class<? extends Resource>, Map<String, List<Resource>>> map = new HashMap<>();

        @Override
        public <T extends Resource> SingleResourceResult<T> create(
                FHIRPersistenceContext context,
                T resource) throws FHIRPersistenceException {
            return createOrUpdate(resource);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Resource> SingleResourceResult<T> read(
                FHIRPersistenceContext context,
                Class<T> resourceType,
                String logicalId) throws FHIRPersistenceException {
            List<Resource> versions = map.getOrDefault(resourceType, Collections.emptyMap()).getOrDefault(logicalId, Collections.emptyList());

            SingleResourceResult.Builder<T> resultBuilder = new SingleResourceResult.Builder<T>()
                    .success(!versions.isEmpty())
                    .resource(!versions.isEmpty() ? (T) versions.get(versions.size() - 1) : null);

            return resultBuilder.build();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Resource> SingleResourceResult<T> vread(
                FHIRPersistenceContext context,
                Class<T> resourceType,
                String logicalId,
                String versionId) throws FHIRPersistenceException {
            List<Resource> versions = map.getOrDefault(resourceType, Collections.emptyMap()).getOrDefault(logicalId, Collections.emptyList());

            int index = Integer.valueOf(versionId) - 1;

            SingleResourceResult.Builder<T> resultBuilder = new SingleResourceResult.Builder<T>()
                    .success(index >= 0 && index < versions.size())
                    .resource((index >= 0 && index < versions.size()) ? (T) versions.get(index) : null);

            return resultBuilder.build();
        }

        @Override
        public <T extends Resource> SingleResourceResult<T> update(
                FHIRPersistenceContext context,
                String logicalId,
                T resource) throws FHIRPersistenceException {
            return createOrUpdate(resource);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Resource> MultiResourceResult<T> history(
                FHIRPersistenceContext context,
                Class<T> resourceType,
                String logicalId) throws FHIRPersistenceException {
            List<T> versions = (List<T>) map.getOrDefault(resourceType, Collections.emptyMap()).getOrDefault(logicalId, Collections.emptyList());

            MultiResourceResult.Builder<T> resultBuilder = new MultiResourceResult.Builder<T>()
                    .success(!versions.isEmpty())
                    .resource(versions);

            return resultBuilder.build();
        }

        @Override
        public MultiResourceResult<Resource> search(
                FHIRPersistenceContext context,
                Class<? extends Resource> resourceType) throws FHIRPersistenceException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTransactional() {
            return true;
        }

        @Override
        public OperationOutcome getHealth() throws FHIRPersistenceException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FHIRPersistenceTransaction getTransaction() {
            return new PersistenceTransactionImpl();
        }

        @Override
        public String generateResourceId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int reindex(
                FHIRPersistenceContext context,
                Builder operationOutcomeResult,
                java.time.Instant tstamp,
                String resourceLogicalId) throws FHIRPersistenceException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourcePayload fetchResourcePayloads(
                Class<? extends Resource> resourceType,
                java.time.Instant fromLastModified,
                java.time.Instant toLastModified,
                Function<ResourcePayload, Boolean> process) throws FHIRPersistenceException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ResourceChangeLogRecord> changes(
                int resourceCount,
                java.time.Instant fromLastModified,
                Long afterResourceId,
                String resourceTypeName) throws FHIRPersistenceException {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        private <T extends Resource> SingleResourceResult<T> createOrUpdate(T resource) {
            Class<? extends Resource> resourceType = resource.getClass();
            String id = (resource.getId() != null) ? resource.getId() : UUID.randomUUID().toString();

            List<Resource> versions = map.computeIfAbsent(resourceType, k -> new HashMap<>())
                .computeIfAbsent(id, k -> new ArrayList<>());

            String versionId = String.valueOf(versions.size() + 1);

            Meta.Builder metaBuilder = (resource.getMeta() != null) ? resource.getMeta().toBuilder() : Meta.builder();
            metaBuilder.versionId(Id.of(versionId)).lastUpdated(Instant.now(ZoneOffset.UTC));

            Resource.Builder resourceBuilder = resource.toBuilder();

            resource = (T) resourceBuilder
                .id(id)
                .meta(metaBuilder.build())
                .build();

            versions.add(resource);

            SingleResourceResult.Builder<T> resultBuilder = new SingleResourceResult.Builder<T>()
                    .success(true)
                    .resource(resource);

            return resultBuilder.build();
        }
    }

    public static class PersistenceTransactionImpl implements FHIRPersistenceTransaction {
        @Override
        public void begin() throws FHIRPersistenceException {
            // do nothing
        }

        @Override
        public void end() throws FHIRPersistenceException {
            // do nothing
        }

        @Override
        public void setRollbackOnly() throws FHIRPersistenceException {
            // do nothing
        }
    }
}
