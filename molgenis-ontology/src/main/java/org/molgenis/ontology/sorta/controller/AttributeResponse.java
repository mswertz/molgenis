package org.molgenis.ontology.sorta.controller;

import static org.molgenis.i18n.LanguageService.getCurrentUserLanguageCode;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.molgenis.data.DataService;
import org.molgenis.data.Range;
import org.molgenis.data.meta.AttributeType;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.security.core.UserPermissionEvaluator;

public class AttributeResponse {
  private static final String BASE_URI = "/api/v1";

  private final String href;
  private final AttributeType fieldType;
  private final String name;
  private final String label;
  private final String description;
  private final List<?> attributes;
  private final List<String> enumOptions;
  private final Long maxLength;
  private final Object refEntity;
  private final String mappedBy;
  private final Boolean auto;
  private final Boolean nillable;
  private final Boolean readOnly;
  private final Object defaultValue;
  private final Boolean labelAttribute;
  private final Boolean unique;
  private final Boolean visible;
  private Boolean lookupAttribute;
  private Boolean isAggregatable;
  private Range range;
  private String expression;
  private String nullableExpression;
  private String visibleExpression;
  private String validationExpression;

  public AttributeResponse(
      String entityParentName,
      EntityType entityType,
      Attribute attr,
      UserPermissionEvaluator permissionService,
      DataService dataService) {
    this(entityParentName, entityType, attr, null, null, permissionService, dataService);
  }

  /**
   * @param attributesSet set of lowercase attribute names to include in response
   * @param attributeExpandsSet set of lowercase attribute names to expand in response
   */
  public AttributeResponse(
      final String entityParentName,
      EntityType entityType,
      Attribute attr,
      Set<String> attributesSet,
      final Map<String, Set<String>> attributeExpandsSet,
      UserPermissionEvaluator permissionService,
      DataService dataService) {
    String attrName = attr.getName();
    this.href = Href.concatMetaAttributeHref(BASE_URI, entityParentName, attrName);

    if (attributesSet == null || attributesSet.contains("fieldType".toLowerCase())) {
      this.fieldType = attr.getDataType();
    } else this.fieldType = null;

    if (attributesSet == null || attributesSet.contains("name".toLowerCase())) {
      this.name = attrName;
    } else this.name = null;

    if (attributesSet == null || attributesSet.contains("label".toLowerCase())) {
      this.label = attr.getLabel(getCurrentUserLanguageCode());
    } else this.label = null;

    if (attributesSet == null || attributesSet.contains("description".toLowerCase())) {
      this.description = attr.getDescription(getCurrentUserLanguageCode());
    } else this.description = null;

    if (attributesSet == null || attributesSet.contains("enumOptions".toLowerCase())) {
      this.enumOptions = attr.getEnumOptions();
    } else this.enumOptions = null;

    if (attributesSet == null || attributesSet.contains("maxLength".toLowerCase())) {
      this.maxLength = attr.getDataType().getMaxLength();
    } else this.maxLength = null;

    if (attributesSet == null || attributesSet.contains("expression".toLowerCase())) {
      this.expression = attr.getExpression();
    } else this.expression = null;

    if (attributesSet == null || attributesSet.contains("refEntity".toLowerCase())) {
      if (attributeExpandsSet != null
          && attributeExpandsSet.containsKey("refEntity".toLowerCase())) {
        Set<String> subAttributesSet = attributeExpandsSet.get("refEntity".toLowerCase());
        this.refEntity =
            attr.hasRefEntity()
                ? new EntityTypeResponse(
                    attr.getRefEntity(),
                    subAttributesSet,
                    Collections.singletonMap("attributes".toLowerCase(), null),
                    permissionService,
                    dataService)
                : null;
      } else {
        this.refEntity =
            attr.hasRefEntity()
                ? new Href(
                    Href.concatMetaEntityHref(BASE_URI, attr.getRefEntity().getId()),
                    String.format("%s/%s", BASE_URI, attr.getRefEntity().getId()))
                : null; // FIXME apply Href escaping fix
      }
    } else this.refEntity = null;

    Attribute mappedByAttr = attr.getMappedBy();
    this.mappedBy = mappedByAttr != null ? mappedByAttr.getName() : null;

    if (attributesSet == null || attributesSet.contains("attributes".toLowerCase())) {
      Iterable<Attribute> attributeParts = attr.getChildren();
      this.attributes =
          attributeParts != null
              ? Lists.newArrayList(
                  Iterables.transform(
                      attributeParts,
                      attribute -> {
                        if (attributeExpandsSet != null
                            && attributeExpandsSet.containsKey("attributes".toLowerCase())) {
                          Set<String> subAttributesSet =
                              attributeExpandsSet.get("attributes".toLowerCase());
                          return new AttributeResponse(
                              entityParentName,
                              entityType,
                              attribute,
                              subAttributesSet,
                              Collections.singletonMap("refEntity".toLowerCase(), null),
                              permissionService,
                              dataService);
                        } else {
                          return Collections.<String, Object>singletonMap(
                              "href",
                              Href.concatMetaAttributeHref(
                                  BASE_URI, entityParentName, attribute.getName()));
                        }
                      }))
              : null;
    } else this.attributes = null;

    if (attributesSet == null || attributesSet.contains("auto".toLowerCase())) {
      this.auto = attr.isAuto();
    } else this.auto = null;

    if (attributesSet == null || attributesSet.contains("nillable".toLowerCase())) {
      this.nillable = attr.isNillable();
    } else this.nillable = null;

    if (attributesSet == null || attributesSet.contains("readOnly".toLowerCase())) {
      this.readOnly = attr.isReadOnly();
    } else this.readOnly = null;

    if (attributesSet == null || attributesSet.contains("defaultValue".toLowerCase())) {
      this.defaultValue = attr.getDefaultValue();
    } else this.defaultValue = null;

    if (attributesSet == null || attributesSet.contains("labelAttribute".toLowerCase())) {
      this.labelAttribute = attr.equals(entityType.getLabelAttribute());
    } else this.labelAttribute = null;

    if (attributesSet == null || attributesSet.contains("unique".toLowerCase())) {
      this.unique = attr.isUnique();
    } else this.unique = null;

    if (attributesSet == null || attributesSet.contains("lookupAttribute".toLowerCase())) {
      this.lookupAttribute = entityType.getLookupAttribute(attr.getName()) != null;
    } else this.lookupAttribute = null;

    if (attributesSet == null || attributesSet.contains("isAggregatable".toLowerCase())) {
      this.isAggregatable = attr.isAggregatable();
    } else this.isAggregatable = null;

    if (attributesSet == null || attributesSet.contains("range".toLowerCase())) {
      this.range = attr.getRange();
    } else this.range = null;

    if (attributesSet == null || attributesSet.contains("isVisible".toLowerCase())) {
      this.visible = attr.isVisible();
    } else this.visible = null;

    if (attributesSet == null || attributesSet.contains("nullableExpression".toLowerCase())) {
      this.nullableExpression = attr.getNullableExpression();
    } else this.nullableExpression = null;

    if (attributesSet == null || attributesSet.contains("visibleExpression".toLowerCase())) {
      this.visibleExpression = attr.getVisibleExpression();
    } else this.visibleExpression = null;

    if (attributesSet == null || attributesSet.contains("validationExpression".toLowerCase())) {
      this.validationExpression = attr.getValidationExpression();
    } else this.validationExpression = null;
  }

  public String getHref() {
    return href;
  }

  public AttributeType getFieldType() {
    return fieldType;
  }

  public String getName() {
    return name;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public List<?> getAttributes() {
    return attributes;
  }

  public List<String> getEnumOptions() {
    return enumOptions;
  }

  public Long getMaxLength() {
    return maxLength;
  }

  public Object getRefEntity() {
    return refEntity;
  }

  public String getMappedBy() {
    return mappedBy;
  }

  public boolean isAuto() {
    return auto;
  }

  public boolean isNillable() {
    return nillable;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public boolean isLabelAttribute() {
    return labelAttribute;
  }

  public boolean isUnique() {
    return unique;
  }

  public boolean isVisible() {
    return visible;
  }

  public Boolean getLookupAttribute() {
    return lookupAttribute;
  }

  public Boolean isAggregatable() {
    return isAggregatable;
  }

  public Range getRange() {
    return range;
  }

  public String getExpression() {
    return expression;
  }

  public String getNullableExpression() {
    return nullableExpression;
  }

  public String getVisibleExpression() {
    return visibleExpression;
  }

  public String getValidationExpression() {
    return validationExpression;
  }
}
