package org.molgenis.data.security;

import org.molgenis.data.meta.model.EntityType;
import org.springframework.security.acls.domain.ObjectIdentityImpl;

public class EntityTypeIdentity extends ObjectIdentityImpl {
  public static final String ENTITY_TYPE = "entityType";

  public EntityTypeIdentity(EntityType entityType) {
    this(entityType.getId());
  }

  public EntityTypeIdentity(String entityTypeId) {
    super(ENTITY_TYPE, entityTypeId);
  }
}
