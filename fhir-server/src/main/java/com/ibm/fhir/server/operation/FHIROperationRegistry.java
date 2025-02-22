/*
 * (C) Copyright IBM Corp. 2016, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.server.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.fhir.exception.FHIROperationException;
import com.ibm.fhir.model.resource.OperationDefinition;
import com.ibm.fhir.model.resource.OperationOutcome.Issue;
import com.ibm.fhir.model.type.Boolean;
import com.ibm.fhir.model.type.code.IssueSeverity;
import com.ibm.fhir.model.type.code.IssueType;
import com.ibm.fhir.model.type.code.ResourceType;
import com.ibm.fhir.model.util.FHIRUtil;
import com.ibm.fhir.server.operation.spi.FHIROperation;
import com.ibm.fhir.validation.FHIRValidator;
import com.ibm.fhir.validation.exception.FHIRValidationException;

public class FHIROperationRegistry {
    private final Logger log = Logger.getLogger(FHIROperationRegistry.class.getName());
    private static final FHIROperationRegistry INSTANCE = new FHIROperationRegistry();
    // Key format: operation name[:resource type]
    // Sample keys: validate:Resource, export:Patient, export
    private Map<String, FHIROperation> operationMap = null;

    private FHIROperationRegistry() {
        operationMap = new TreeMap<String, FHIROperation>();
        ServiceLoader<FHIROperation> operations = ServiceLoader.load(FHIROperation.class);
        // https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html#iterator--
        Iterator<FHIROperation> iterator = operations.iterator();
        while (iterator.hasNext()) {
            String operationName = "unknown name";
            try {
                FHIROperation operation = iterator.next();
                log.fine("Found FHIROperation implementation class: " + operation.getClass().getName());
                operationName = operation.getName();
                if (!isValid(operation)) {
                    log.severe("Operation $" + operationName + " has failed validation and will be skipped.");
                    continue;
                }
                List<ResourceType> operationResourceTypes = operation.getDefinition().getResource();
                if (operationResourceTypes == null || operationResourceTypes.isEmpty() || Boolean.TRUE.equals(operation.getDefinition().getSystem())) {
                    if (operationMap.putIfAbsent(operation.getName(), operation) != null) {
                        throw new IllegalStateException("Found duplicated operation name: " + operation.getName());
                    }
                } else {
                    // First, check if there is already an operation defined for all resource types.
                    String tmpKey = operation.getName() + ":" + "Resource";
                    if (operationMap.containsKey(tmpKey)) {
                        throw new IllegalStateException("There is already operation defined for all resource types: "
                            + operation.getName() + "; Conflict Operations: " + operation.getDefinition().getName()
                            + " <--> " + operationMap.get(tmpKey).getDefinition().getName());
                    }
                    // Then check if there is already operation defined for the required resource types.
                    for (ResourceType operationResourceType : operationResourceTypes) {
                        tmpKey = operation.getName() + ":" + operationResourceType.getValue();
                        if (operationMap.putIfAbsent(tmpKey, operation) != null) {
                            throw new IllegalStateException("Found duplicated operation name plus resource type: "
                                + operation.getName() + "-" + operationResourceType.getValue()
                                + "; Conflict Operations: " + operation.getDefinition().getName()
                                + " <--> " + operationMap.get(tmpKey).getDefinition().getName());
                        }
                    }
                }
            } catch (ServiceConfigurationError | FHIRValidationException e) {
                log.log(Level.SEVERE, "Unable to validate operation $" + operationName + ". This operation will be skipped.", e);
            }
        }

        if (log.isLoggable(Level.FINE)) {
            log.fine("Discovered " + operationMap.size() + " custom operation implementations:");
            log.fine(operationMap.toString());
        }
    }

    private boolean isValid(FHIROperation operation) throws FHIRValidationException, FHIRValidationException {
        OperationDefinition opDef = operation.getDefinition();
        List<Issue> issues = FHIRValidator.validator().validate(opDef);
        if (!issues.isEmpty()) {
            for (Issue issue : issues) {
                if (log.isLoggable(Level.FINE)) {
                    log.fine("Issue: " + issue.getCode().getValue() + ":"
                            + issue.getSeverity().getValue() + ":" + issue.getDetails().getText().getValue());
                }
                if (issue.getSeverity().equals(IssueSeverity.ERROR)
                        || issue.getSeverity().equals(IssueSeverity.FATAL)) {
                    return false;
                }
            }
        }
        if (operation.getName() == null || !operation.getName().equals(opDef.getCode().getValue())) {
            log.info("Name mismatch: the operation '" + operation.getName() + "' must match the OperationDefinition code '" +
                    opDef.getCode() + "'");
            return false;
        }
        if (opDef.getUrl() == null) {
            log.info("Operation " + operation.getName() + " is missing a 'url'; this field is required by the IBM FHIR Server");
            return false;
        }
        return true;
    }

    public List<String> getOperationNames() {
        return Collections.unmodifiableList(new ArrayList<String>(operationMap.keySet()));
    }

    public static FHIROperationRegistry getInstance() {
        return INSTANCE;
    }

    public FHIROperation getOperation(String name) throws FHIROperationException {
        FHIROperation operation = operationMap.get(name);
        if (operation == null) {
            // Check if there is an operation defined for all resource types.
            operation = operationMap.get(name.split(":")[0] + ":" + "Resource");
            if (operation == null) {
                String msg = "Operation with name: '" + name + "' was not found";
                throw new FHIROperationException(msg)
                    .withIssue(FHIRUtil.buildOperationOutcomeIssue(IssueSeverity.FATAL, IssueType.NOT_SUPPORTED, msg));
            }
        }
        return operation;
    }
}
