/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package org.exist.security.realm.jmmc;

import org.exist.security.SchemaType;

/**
 * An attempt at defining new account metadata for eXist-db.
 *
 * <p>
 * Note: eXist-db does not support the definition of custom account metadata.
 * As a result XQuery's sm:get-account-metadata() will not return the
 * metadata for the keys defined below. Yet the metadata is saved to the
 * account description.
 * </p>
 *
 * @author Patrick Bernaud
 */
public enum JMMCSchemaType implements SchemaType {
    AFFILIATION("http://exist.jmmc.fr/user/affiliation", "Affiliation");
    
    private final String namespace;
    private final String alias;

    JMMCSchemaType(final String namespace, final String alias) {
        this.namespace = namespace;
        this.alias = alias;
    }
    
    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getAlias() {
        return alias;
    }
}
