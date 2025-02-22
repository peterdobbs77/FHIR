/*
 * (C) Copyright IBM Corp. 2019, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.model.type.code;

import com.ibm.fhir.model.annotation.System;
import com.ibm.fhir.model.type.Code;
import com.ibm.fhir.model.type.Extension;
import com.ibm.fhir.model.type.String;

import java.util.Collection;
import java.util.Objects;

import javax.annotation.Generated;

@System("http://hl7.org/fhir/assert-response-code-types")
@Generated("com.ibm.fhir.tools.CodeGenerator")
public class AssertionResponseTypes extends Code {
    /**
     * okay
     * 
     * <p>Response code is 200.
     */
    public static final AssertionResponseTypes OKAY = AssertionResponseTypes.builder().value(Value.OKAY).build();

    /**
     * created
     * 
     * <p>Response code is 201.
     */
    public static final AssertionResponseTypes CREATED = AssertionResponseTypes.builder().value(Value.CREATED).build();

    /**
     * noContent
     * 
     * <p>Response code is 204.
     */
    public static final AssertionResponseTypes NO_CONTENT = AssertionResponseTypes.builder().value(Value.NO_CONTENT).build();

    /**
     * notModified
     * 
     * <p>Response code is 304.
     */
    public static final AssertionResponseTypes NOT_MODIFIED = AssertionResponseTypes.builder().value(Value.NOT_MODIFIED).build();

    /**
     * bad
     * 
     * <p>Response code is 400.
     */
    public static final AssertionResponseTypes BAD = AssertionResponseTypes.builder().value(Value.BAD).build();

    /**
     * forbidden
     * 
     * <p>Response code is 403.
     */
    public static final AssertionResponseTypes FORBIDDEN = AssertionResponseTypes.builder().value(Value.FORBIDDEN).build();

    /**
     * notFound
     * 
     * <p>Response code is 404.
     */
    public static final AssertionResponseTypes NOT_FOUND = AssertionResponseTypes.builder().value(Value.NOT_FOUND).build();

    /**
     * methodNotAllowed
     * 
     * <p>Response code is 405.
     */
    public static final AssertionResponseTypes METHOD_NOT_ALLOWED = AssertionResponseTypes.builder().value(Value.METHOD_NOT_ALLOWED).build();

    /**
     * conflict
     * 
     * <p>Response code is 409.
     */
    public static final AssertionResponseTypes CONFLICT = AssertionResponseTypes.builder().value(Value.CONFLICT).build();

    /**
     * gone
     * 
     * <p>Response code is 410.
     */
    public static final AssertionResponseTypes GONE = AssertionResponseTypes.builder().value(Value.GONE).build();

    /**
     * preconditionFailed
     * 
     * <p>Response code is 412.
     */
    public static final AssertionResponseTypes PRECONDITION_FAILED = AssertionResponseTypes.builder().value(Value.PRECONDITION_FAILED).build();

    /**
     * unprocessable
     * 
     * <p>Response code is 422.
     */
    public static final AssertionResponseTypes UNPROCESSABLE = AssertionResponseTypes.builder().value(Value.UNPROCESSABLE).build();

    private volatile int hashCode;

    private AssertionResponseTypes(Builder builder) {
        super(builder);
    }

    /**
     * Get the value of this AssertionResponseTypes as an enum constant.
     * @deprecated replaced by {@link #getValueAsEnum()}
     */
    @Deprecated
    public ValueSet getValueAsEnumConstant() {
        return (value != null) ? ValueSet.from(value) : null;
    }

    /**
     * Get the value of this AssertionResponseTypes as an enum constant.
     */
    public Value getValueAsEnum() {
        return (value != null) ? Value.from(value) : null;
    }

    /**
     * Factory method for creating AssertionResponseTypes objects from a passed enum value.
     * @deprecated replaced by {@link #of(Value)}
     */
    @Deprecated
    public static AssertionResponseTypes of(ValueSet value) {
        switch (value) {
        case OKAY:
            return OKAY;
        case CREATED:
            return CREATED;
        case NO_CONTENT:
            return NO_CONTENT;
        case NOT_MODIFIED:
            return NOT_MODIFIED;
        case BAD:
            return BAD;
        case FORBIDDEN:
            return FORBIDDEN;
        case NOT_FOUND:
            return NOT_FOUND;
        case METHOD_NOT_ALLOWED:
            return METHOD_NOT_ALLOWED;
        case CONFLICT:
            return CONFLICT;
        case GONE:
            return GONE;
        case PRECONDITION_FAILED:
            return PRECONDITION_FAILED;
        case UNPROCESSABLE:
            return UNPROCESSABLE;
        default:
            throw new IllegalStateException(value.name());
        }
    }

    /**
     * Factory method for creating AssertionResponseTypes objects from a passed enum value.
     */
    public static AssertionResponseTypes of(Value value) {
        switch (value) {
        case OKAY:
            return OKAY;
        case CREATED:
            return CREATED;
        case NO_CONTENT:
            return NO_CONTENT;
        case NOT_MODIFIED:
            return NOT_MODIFIED;
        case BAD:
            return BAD;
        case FORBIDDEN:
            return FORBIDDEN;
        case NOT_FOUND:
            return NOT_FOUND;
        case METHOD_NOT_ALLOWED:
            return METHOD_NOT_ALLOWED;
        case CONFLICT:
            return CONFLICT;
        case GONE:
            return GONE;
        case PRECONDITION_FAILED:
            return PRECONDITION_FAILED;
        case UNPROCESSABLE:
            return UNPROCESSABLE;
        default:
            throw new IllegalStateException(value.name());
        }
    }

    /**
     * Factory method for creating AssertionResponseTypes objects from a passed string value.
     * 
     * @param value
     *     A string that matches one of the allowed code values
     * @throws IllegalArgumentException
     *     If the passed string cannot be parsed into an allowed code value
     */
    public static AssertionResponseTypes of(java.lang.String value) {
        return of(Value.from(value));
    }

    /**
     * Inherited factory method for creating AssertionResponseTypes objects from a passed string value.
     * 
     * @param value
     *     A string that matches one of the allowed code values
     * @throws IllegalArgumentException
     *     If the passed string cannot be parsed into an allowed code value
     */
    public static String string(java.lang.String value) {
        return of(Value.from(value));
    }

    /**
     * Inherited factory method for creating AssertionResponseTypes objects from a passed string value.
     * 
     * @param value
     *     A string that matches one of the allowed code values
     * @throws IllegalArgumentException
     *     If the passed string cannot be parsed into an allowed code value
     */
    public static Code code(java.lang.String value) {
        return of(Value.from(value));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AssertionResponseTypes other = (AssertionResponseTypes) obj;
        return Objects.equals(id, other.id) && Objects.equals(extension, other.extension) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if (result == 0) {
            result = Objects.hash(id, extension, value);
            hashCode = result;
        }
        return result;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.id(id);
        builder.extension(extension);
        builder.value(value);
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Code.Builder {
        private Builder() {
            super();
        }

        @Override
        public Builder id(java.lang.String id) {
            return (Builder) super.id(id);
        }

        @Override
        public Builder extension(Extension... extension) {
            return (Builder) super.extension(extension);
        }

        @Override
        public Builder extension(Collection<Extension> extension) {
            return (Builder) super.extension(extension);
        }

        @Override
        public Builder value(java.lang.String value) {
            return (value != null) ? (Builder) super.value(Value.from(value).value()) : this;
        }

        /**
         * @deprecated replaced by  {@link #value(Value)}
         */
        @Deprecated
        public Builder value(ValueSet value) {
            return (value != null) ? (Builder) super.value(value.value()) : this;
        }

        /**
         * Primitive value for code
         * 
         * @param value
         *     An enum constant for AssertionResponseTypes
         * 
         * @return
         *     A reference to this Builder instance
         */
        public Builder value(Value value) {
            return (value != null) ? (Builder) super.value(value.value()) : this;
        }

        @Override
        public AssertionResponseTypes build() {
            return new AssertionResponseTypes(this);
        }
    }

    @Deprecated
    public enum ValueSet {
        /**
         * okay
         * 
         * <p>Response code is 200.
         */
        OKAY("okay"),

        /**
         * created
         * 
         * <p>Response code is 201.
         */
        CREATED("created"),

        /**
         * noContent
         * 
         * <p>Response code is 204.
         */
        NO_CONTENT("noContent"),

        /**
         * notModified
         * 
         * <p>Response code is 304.
         */
        NOT_MODIFIED("notModified"),

        /**
         * bad
         * 
         * <p>Response code is 400.
         */
        BAD("bad"),

        /**
         * forbidden
         * 
         * <p>Response code is 403.
         */
        FORBIDDEN("forbidden"),

        /**
         * notFound
         * 
         * <p>Response code is 404.
         */
        NOT_FOUND("notFound"),

        /**
         * methodNotAllowed
         * 
         * <p>Response code is 405.
         */
        METHOD_NOT_ALLOWED("methodNotAllowed"),

        /**
         * conflict
         * 
         * <p>Response code is 409.
         */
        CONFLICT("conflict"),

        /**
         * gone
         * 
         * <p>Response code is 410.
         */
        GONE("gone"),

        /**
         * preconditionFailed
         * 
         * <p>Response code is 412.
         */
        PRECONDITION_FAILED("preconditionFailed"),

        /**
         * unprocessable
         * 
         * <p>Response code is 422.
         */
        UNPROCESSABLE("unprocessable");

        private final java.lang.String value;

        ValueSet(java.lang.String value) {
            this.value = value;
        }

        /**
         * @return
         *     The java.lang.String value of the code represented by this enum
         */
        public java.lang.String value() {
            return value;
        }

        /**
         * Factory method for creating AssertionResponseTypes.Value values from a passed string value.
         * 
         * @param value
         *     A string that matches one of the allowed code values
         * @throws IllegalArgumentException
         *     If the passed string cannot be parsed into an allowed code value
         */
        public static ValueSet from(java.lang.String value) {
            for (ValueSet c : ValueSet.values()) {
                if (c.value.equals(value)) {
                    return c;
                }
            }
            throw new IllegalArgumentException(value);
        }
    }

    public enum Value {
        /**
         * okay
         * 
         * <p>Response code is 200.
         */
        OKAY("okay"),

        /**
         * created
         * 
         * <p>Response code is 201.
         */
        CREATED("created"),

        /**
         * noContent
         * 
         * <p>Response code is 204.
         */
        NO_CONTENT("noContent"),

        /**
         * notModified
         * 
         * <p>Response code is 304.
         */
        NOT_MODIFIED("notModified"),

        /**
         * bad
         * 
         * <p>Response code is 400.
         */
        BAD("bad"),

        /**
         * forbidden
         * 
         * <p>Response code is 403.
         */
        FORBIDDEN("forbidden"),

        /**
         * notFound
         * 
         * <p>Response code is 404.
         */
        NOT_FOUND("notFound"),

        /**
         * methodNotAllowed
         * 
         * <p>Response code is 405.
         */
        METHOD_NOT_ALLOWED("methodNotAllowed"),

        /**
         * conflict
         * 
         * <p>Response code is 409.
         */
        CONFLICT("conflict"),

        /**
         * gone
         * 
         * <p>Response code is 410.
         */
        GONE("gone"),

        /**
         * preconditionFailed
         * 
         * <p>Response code is 412.
         */
        PRECONDITION_FAILED("preconditionFailed"),

        /**
         * unprocessable
         * 
         * <p>Response code is 422.
         */
        UNPROCESSABLE("unprocessable");

        private final java.lang.String value;

        Value(java.lang.String value) {
            this.value = value;
        }

        /**
         * @return
         *     The java.lang.String value of the code represented by this enum
         */
        public java.lang.String value() {
            return value;
        }

        /**
         * Factory method for creating AssertionResponseTypes.Value values from a passed string value.
         * 
         * @param value
         *     A string that matches one of the allowed code values
         * @return
         *     The corresponding AssertionResponseTypes.Value or null if a null value was passed
         * @throws IllegalArgumentException
         *     If the passed string is not null and cannot be parsed into an allowed code value
         */
        public static Value from(java.lang.String value) {
            if (value == null) {
                return null;
            }
            switch (value) {
            case "okay":
                return OKAY;
            case "created":
                return CREATED;
            case "noContent":
                return NO_CONTENT;
            case "notModified":
                return NOT_MODIFIED;
            case "bad":
                return BAD;
            case "forbidden":
                return FORBIDDEN;
            case "notFound":
                return NOT_FOUND;
            case "methodNotAllowed":
                return METHOD_NOT_ALLOWED;
            case "conflict":
                return CONFLICT;
            case "gone":
                return GONE;
            case "preconditionFailed":
                return PRECONDITION_FAILED;
            case "unprocessable":
                return UNPROCESSABLE;
            default:
                throw new IllegalArgumentException(value);
            }
        }
    }
}
