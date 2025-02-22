/*
 * (C) Copyright IBM Corp. 2017, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.operation.document;

import static com.ibm.fhir.model.type.String.string;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ibm.fhir.exception.FHIROperationException;
import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Bundle.Entry;
import com.ibm.fhir.model.resource.Composition;
import com.ibm.fhir.model.resource.OperationDefinition;
import com.ibm.fhir.model.resource.Parameters;
import com.ibm.fhir.model.resource.Parameters.Parameter;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.type.Identifier;
import com.ibm.fhir.model.type.Instant;
import com.ibm.fhir.model.type.Meta;
import com.ibm.fhir.model.type.Reference;
import com.ibm.fhir.model.type.Uri;
import com.ibm.fhir.model.type.code.BundleType;
import com.ibm.fhir.registry.FHIRRegistry;
import com.ibm.fhir.server.operation.spi.AbstractOperation;
import com.ibm.fhir.server.operation.spi.FHIROperationContext;
import com.ibm.fhir.server.operation.spi.FHIRResourceHelpers;
import com.ibm.fhir.server.operation.spi.FHIRRestOperationResponse;
import com.ibm.fhir.server.util.FHIROperationUtil;

public class DocumentOperation extends AbstractOperation {
    @Override
    protected OperationDefinition buildOperationDefinition() {
        return FHIRRegistry.getInstance().getResource("http://hl7.org/fhir/OperationDefinition/Composition-document",
                OperationDefinition.class);
    }

    @Override
    protected Parameters doInvoke(FHIROperationContext operationContext, Class<? extends Resource> resourceType, String logicalId,
            String versionId, Parameters parameters, FHIRResourceHelpers resourceHelper) throws FHIROperationException {
        try {
            Composition composition = null;

            Resource resource = resourceHelper.doRead("Composition", logicalId, false, false, null);
            if (resource == null) {
                throw new FHIROperationException("Could not find composition with id: " + logicalId);
            }

            composition = (Composition) resource;

            Bundle bundle = buildDocument(operationContext, composition, resourceHelper);
            Parameter persistParameter = getParameter(parameters, "persist");

            if (persistParameter != null) {
                com.ibm.fhir.model.type.Boolean persistParameterValue = persistParameter.getValue().as(com.ibm.fhir.model.type.Boolean.class); //getValueBoolean();

                if (persistParameterValue != null) {
                    boolean persist = false;

                    if (persistParameterValue.getValue() != null) {
                        persist = persistParameterValue.getValue();
                    }

                    if (persist) {
                        // FHIRResourceHelper resourceHelper = (FHIRResourceHelper) operationContext.getProperty(FHIROperationContext.PROPNAME_RESOURCE_HELPER;
                        FHIRRestOperationResponse response = resourceHelper.doCreate("Bundle", bundle, null, false);
                        // Use the responded bundle to create response to client.
                        bundle = (Bundle)response.getResource();
                        URI locationURI = response.getLocationURI();
                        operationContext.setProperty(FHIROperationContext.PROPNAME_LOCATION_URI, locationURI);
                    }
                }
            }


            return FHIROperationUtil.getOutputParameters(bundle);
        } catch (FHIROperationException e) {
            throw e;
        } catch (Exception e) {
            throw new FHIROperationException("An error occurred during the document operation", e);
        }
    }

    private Bundle buildDocument(FHIROperationContext operationContext, Composition composition, FHIRResourceHelpers resourceHelper) throws Exception {
        //Bundle document = factory.createBundle();
        //document.setType(factory.createBundleType().withValue(BundleTypeList.DOCUMENT));

        Bundle.Builder documentBuilder = Bundle.builder().type(BundleType.DOCUMENT);

        // the composition is the first bundle entry in the document
        Bundle.Entry.Builder entryBuilder = Entry.builder(); //createBundleEntry();
        //ResourceContainer container = factory.createResourceContainer();
        //container.setComposition(composition);
        //bundleEntry.setResource(container);

        //ResourceContainer container = factory.createResourceContainer();
        //FHIRUtil.setResourceContainerResource(container, resource);
        //bundleEntry.setResource(composition);
        entryBuilder.resource(composition);

        setFullUrl(operationContext, entryBuilder, "Composition/" + composition.getId());

        //document.getEntry().add(bundleEntry);
        documentBuilder.entry(entryBuilder.build());

        Map<String, Resource> resources = new HashMap<String, Resource>();

        // Composition.subject
        addBundleEntry(operationContext, documentBuilder, composition.getSubject(), resourceHelper, resources);

        // Composition.author
        for (Reference author : composition.getAuthor()) {
            addBundleEntry(operationContext, documentBuilder, author, resourceHelper, resources);
        }

        // Composition.attester.party
        for (Composition.Attester attester : composition.getAttester()) {
            addBundleEntry(operationContext, documentBuilder, attester.getParty(), resourceHelper, resources);
        }

        // Composition.custodian
        addBundleEntry(operationContext, documentBuilder, composition.getCustodian(), resourceHelper, resources);

        // Composition.event.detail
        for (Composition.Event event : composition.getEvent()) {
            for (Reference detail : event.getDetail()) {
                addBundleEntry(operationContext, documentBuilder, detail, resourceHelper, resources);
            }
        }

        // Composition.encounter
        addBundleEntry(operationContext, documentBuilder, composition.getEncounter(), resourceHelper, resources);

        // Composition.section.entry
        addBundleEntries(operationContext, documentBuilder, composition.getSection(), resourceHelper, resources);


        return documentBuilder.timestamp(Instant.now(ZoneOffset.UTC))
                .identifier(Identifier.builder()
                        .system(Uri.of("http://hl7.org/fhir/OperationDefinition/Composition-document")).value(string("urn:uuid:" + UUID.randomUUID().toString()))
                        .build())
                .meta(Meta.builder().lastUpdated(Instant.now(ZoneOffset.UTC)).build())
                .build();
    }

    private void addBundleEntry(FHIROperationContext operationContext, Bundle.Builder documentBuilder, Reference reference, FHIRResourceHelpers resourceHelper, Map<String, Resource> resources) throws Exception {;
        if (reference == null) {
            return;
        }

        if (reference.getReference() == null) {
            throw new FHIROperationException("Empty reference object is not allowed");
        }

        String referenceValue = reference.getReference().getValue();
        if (referenceValue == null) {
            throw new FHIROperationException("Empty reference value is not allowed");
        }

        Resource resource = resources.get(referenceValue);

        if (resource == null) {
            String[] referenceTokens = referenceValue.split("/");

            // assumption: references will be relative {resourceTypeName}/{logicalId}
            if (referenceTokens.length != 2) {
                throw new FHIROperationException("Could not parse reference value: " + referenceValue);
            }

            String resourceTypeName = referenceTokens[0];
            String logicalId = referenceTokens[1];

            resource = resourceHelper.doRead(resourceTypeName, logicalId, false, false, null);

            if (resource == null) {
                throw new FHIROperationException("Could not find resource for reference value: " + referenceValue);
            }

            resources.put(referenceValue, resource);

            // create a bundle entry for the resource
            //BundleEntry bundleEntry = factory.createBundleEntry();

            //ResourceContainer container = factory.createResourceContainer();
            //FHIRUtil.setResourceContainerResource(container, resource);

            Bundle.Entry.Builder entryBuilder = Entry.builder(); //createBundleEntry();
            entryBuilder.resource(resource);

            setFullUrl(operationContext, entryBuilder, referenceValue);

            //document.getEntry().add(bundleEntry);
            documentBuilder.entry(entryBuilder.build());
        }
    }

    private void addBundleEntries(FHIROperationContext operationContext, Bundle.Builder documentBuilder, List<Composition.Section> sections, FHIRResourceHelpers resourceHelper, Map<String, Resource> resources) throws Exception {
        for (Composition.Section section : sections) {
            // process entries for this section
            for (Reference entry : section.getEntry()) {
                addBundleEntry(operationContext, documentBuilder, entry, resourceHelper, resources);
            }

            // process subsections
            addBundleEntries(operationContext, documentBuilder, section.getSection(), resourceHelper, resources);
        }
    }

    private void setFullUrl(FHIROperationContext operationContext, Bundle.Entry.Builder entryBuilder, String referenceValue) {
        String requestBaseURI = (String) operationContext.getProperty(FHIROperationContext.PROPNAME_REQUEST_BASE_URI);
        if (requestBaseURI != null) {
            entryBuilder.fullUrl(uri(requestBaseURI + "/" + referenceValue));
        }
    }

    private static Uri uri(String uri) {
        return Uri.builder().value(uri).build();
    }
}
