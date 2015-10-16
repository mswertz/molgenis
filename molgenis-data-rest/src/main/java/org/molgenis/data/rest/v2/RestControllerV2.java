package org.molgenis.data.rest.v2;

import static java.util.Objects.requireNonNull;
import static org.molgenis.data.rest.v2.RestControllerV2.BASE_URI;
import static org.molgenis.util.MolgenisDateFormat.getDateFormat;
import static org.molgenis.util.MolgenisDateFormat.getDateTimeFormat;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.molgenis.MolgenisFieldTypes.FieldTypeEnum;
import org.molgenis.data.AggregateQuery;
import org.molgenis.data.AggregateResult;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.MolgenisDataAccessException;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.data.MolgenisQueryException;
import org.molgenis.data.Query;
import org.molgenis.data.UnknownAttributeException;
import org.molgenis.data.UnknownEntityException;
import org.molgenis.data.rest.EntityPager;
import org.molgenis.data.rest.Href;
import org.molgenis.data.rest.service.RestService;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.data.validation.MolgenisValidationException;
import org.molgenis.file.FileMeta;
import org.molgenis.security.core.MolgenisPermissionService;
import org.molgenis.util.ErrorMessageResponse;
import org.molgenis.util.ErrorMessageResponse.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping(BASE_URI)
class RestControllerV2
{
	private static final Logger LOG = LoggerFactory.getLogger(RestControllerV2.class);

	static final int MAX_ENTITIES = 1000;

	public static final String BASE_URI = "/api/v2";

	private final DataService dataService;
	private final RestService restService;
	private final MolgenisPermissionService permissionService;

	// Exceptions
	static MolgenisDataException EXCEPTION_MAX_ENTITIES_EXCEEDED = new MolgenisDataException(
			"Operation failed. Max " + MAX_ENTITIES + " entities are allowed");
	static MolgenisDataException EXCEPTION_NO_ENTITIES = new MolgenisDataException(
			"Operation failed. No entities to update");

	static UnknownEntityException createUnknownEntityException(String entityName)
	{
		return new UnknownEntityException("Operation failed. Unknown entity: '" + entityName + "'");
	}

	static UnknownAttributeException createUnknownAttributeException(String entityName, String attributeName)
	{
		return new UnknownAttributeException(
				"Operation failed. Unknown attribute: '" + attributeName + "', of entity: '" + entityName + "'");
	}

	static MolgenisDataAccessException createMolgenisDataAccessExceptionReadOnlyAttribute(String entityName,
			String attributeName)
	{
		return new MolgenisDataAccessException(
				"Operation failed. Attribute '" + attributeName + "' of entity '" + entityName + "' is readonly");
	}

	static MolgenisDataException createMolgenisDataExceptionUnknownIdentifier(int count)
	{
		return new MolgenisDataException("Operation failed. Unknown identifier on index " + count);
	}

	static MolgenisDataException createMolgenisDataExceptionIdentifierAndValue()
	{
		return new MolgenisDataException("Operation failed. Entities must provide only an identifier and a value");
	}

	static UnknownEntityException createUnknownEntityExceptionNotValidId(String id)
	{
		return new UnknownEntityException("Operation failed. Identifier: " + id + " is not valid");
	}

	@Autowired
	public RestControllerV2(DataService dataService, MolgenisPermissionService permissionService,
			RestService restService)
	{
		this.dataService = requireNonNull(dataService);
		this.permissionService = requireNonNull(permissionService);
		this.restService = requireNonNull(restService);
	}

	/**
	 * Retrieve an entity instance by id, optionally specify which attributes to include in the response.
	 * 
	 * @param entityName
	 * @param id
	 * @param attributeFilter
	 * @return
	 */
	@RequestMapping(value = "/{entityName}/{id:.+}", method = GET)
	@ResponseBody
	public Map<String, Object> retrieveEntity(@PathVariable("entityName") String entityName,
			@PathVariable("id") Object id,
			@RequestParam(value = "attrs", required = false) AttributeFilter attributeFilter)
	{
		Entity entity = dataService.findOne(entityName, id);
		if (entity == null)
		{
			throw new UnknownEntityException(entityName + " [" + id + "] not found");
		}

		return createEntityResponse(entity, attributeFilter, true);
	}

	@RequestMapping(value = "/{entityName}/{id:.+}", method = POST, params = "_method=GET")
	@ResponseBody
	public Map<String, Object> retrieveEntityPost(@PathVariable("entityName") String entityName,
			@PathVariable("id") Object id,
			@RequestParam(value = "attrs", required = false) AttributeFilter attributeFilter)
	{
		Entity entity = dataService.findOne(entityName, id);
		if (entity == null)
		{
			throw new UnknownEntityException(entityName + " [" + id + "] not found");
		}

		return createEntityResponse(entity, attributeFilter, true);
	}

	@RequestMapping(value = "/{entityName}/{id:.+}", method = DELETE)
	@ResponseStatus(NO_CONTENT)
	public void deleteEntity(@PathVariable("entityName") String entityName, @PathVariable("id") Object id)
	{
		dataService.delete(entityName, id);
	}

	/**
	 * Retrieve an entity collection, optionally specify which attributes to include in the response.
	 * 
	 * @param entityName
	 * @param request
	 * @param attributes
	 * @return
	 */
	@RequestMapping(value = "/{entityName}", method = GET)
	@ResponseBody
	public EntityCollectionResponseV2 retrieveEntityCollection(@PathVariable("entityName") String entityName,
			@Valid EntityCollectionRequestV2 request)
	{
		return createEntityCollectionResponse(entityName, request);
	}

	@RequestMapping(value = "/{entityName}", method = POST, params = "_method=GET")
	@ResponseBody
	public EntityCollectionResponseV2 retrieveEntityCollectionPost(@PathVariable("entityName") String entityName,
			@Valid EntityCollectionRequestV2 request)
	{
		return createEntityCollectionResponse(entityName, request);
	}

	/**
	 * Retrieve attribute meta data
	 * 
	 * @param entityName
	 * @param attributeName
	 * @return
	 */
	@RequestMapping(value = "/{entityName}/meta/{attributeName}", method = GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public AttributeMetaDataResponseV2 retrieveEntityAttributeMeta(@PathVariable("entityName") String entityName,
			@PathVariable("attributeName") String attributeName)
	{
		return createAttributeMetaDataResponse(entityName, attributeName);
	}

	@RequestMapping(value = "/{entityName}/meta/{attributeName}", method = POST, params = "_method=GET", produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public AttributeMetaDataResponseV2 retrieveEntityAttributeMetaPost(@PathVariable("entityName") String entityName,
			@PathVariable("attributeName") String attributeName)
	{
		return createAttributeMetaDataResponse(entityName, attributeName);
	}

	/**
	 * Try to create multiple entities in one transaction. If one fails all fails.
	 * 
	 * @param entityName
	 *            name of the entity where the entities are going to be added.
	 * @param request
	 *            EntityCollectionCreateRequestV2
	 * @param response
	 *            HttpServletResponse
	 * @return EntityCollectionCreateResponseBodyV2
	 * @throws Exception
	 */
	@RequestMapping(value = "/{entityName}", method = POST, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public EntityCollectionBatchCreateResponseBodyV2 createEntities(@PathVariable("entityName") String entityName,
			@RequestBody EntityCollectionBatchRequestV2 request, HttpServletResponse response) throws Exception
	{
		final EntityMetaData meta = dataService.getEntityMetaData(entityName);
		this.generalChecksForBachOperations(request, meta, entityName);

		try
		{
			final List<Entity> entities = request.getEntities().stream().map(e -> this.restService.toEntity(meta, e))
					.collect(Collectors.toList());
			final EntityCollectionBatchCreateResponseBodyV2 responseBody = new EntityCollectionBatchCreateResponseBodyV2();
			final List<String> ids = new ArrayList<String>();

			// Add all entities
			this.dataService.add(entityName, entities);

			for (Entity entity : entities)
			{
				String id = entity.getIdValue().toString();
				ids.add(id.toString());
				responseBody.getResources().add(new AutoValue_ResourcesResponseV2(
						Href.concatEntityHref(RestControllerV2.BASE_URI, entityName, id)));
			}

			responseBody.setLocation(Href.concatEntityCollectionHref(RestControllerV2.BASE_URI, entityName,
					meta.getIdAttribute().getName(), ids));

			response.setStatus(HttpServletResponse.SC_CREATED);
			return responseBody;
		}
		catch (Exception e)
		{
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			throw e;
		}

	}

	/**
	 * Try to update multiple entities in one transaction. If one fails all fails.
	 * 
	 * @param entityName
	 *            name of the entity where the entities are going to be added.
	 * @param request
	 *            EntityCollectionCreateRequestV2
	 * @param response
	 *            HttpServletResponse
	 * @throws Exception
	 */
	@RequestMapping(value = "/{entityName}", method = PUT)
	public synchronized void updateEntities(@PathVariable("entityName") String entityName,
			@RequestBody EntityCollectionBatchRequestV2 request, HttpServletResponse response) throws Exception
	{
		final EntityMetaData meta = dataService.getEntityMetaData(entityName);
		this.generalChecksForBachOperations(request, meta, entityName);

		try
		{
			final List<Entity> entities = request.getEntities().stream().map(e -> this.restService.toEntity(meta, e))
					.collect(Collectors.toList());

			// update all entities
			this.dataService.update(entityName, entities);
			response.setStatus(HttpServletResponse.SC_OK);
		}
		catch (Exception e)
		{
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			throw e;
		}
	}

	/**
	 * 
	 * @param entityName
	 *            The name of the entity to update
	 * @param attributeName
	 *            The name of the attribute to update
	 * @param request
	 *            EntityCollectionBatchRequestV2
	 * @param response
	 *            HttpServletResponse
	 * @throws Exception
	 */
	@RequestMapping(value = "/{entityName}/{attributeName}", method = PUT)
	@ResponseStatus(OK)
	public synchronized void updateAttribute(@PathVariable("entityName") String entityName,
			@PathVariable("attributeName") String attributeName, @RequestBody EntityCollectionBatchRequestV2 request,
			HttpServletResponse response) throws Exception
	{
		final EntityMetaData meta = dataService.getEntityMetaData(entityName);
		this.generalChecksForBachOperations(request, meta, entityName);

		try
		{
			AttributeMetaData attr = meta.getAttribute(attributeName);
			if (attr == null)
			{
				throw RestControllerV2.createUnknownAttributeException(entityName, attributeName);
			}

			if (attr.isReadonly())
			{
				throw RestControllerV2.createMolgenisDataAccessExceptionReadOnlyAttribute(entityName, attributeName);
			}

			final List<Entity> entities = request.getEntities().stream().filter(e -> e.size() == 2)
					.map(e -> this.restService.toEntity(meta, e)).collect(Collectors.toList());
			if (entities.size() != request.getEntities().size())
			{
				throw RestControllerV2.createMolgenisDataExceptionIdentifierAndValue();
			}

			final List<Entity> updatedEntities = new ArrayList<Entity>();
			int count = 0;
			for (Entity entity : entities)
			{
				String id = checkForEntityId(entity, count);

				Entity originalEntity = dataService.findOne(entityName, id);
				if (originalEntity == null)
				{
					throw RestControllerV2.createUnknownEntityExceptionNotValidId(id);
				}

				Object value = this.restService.toEntityValue(attr, entity.get(attributeName));
				originalEntity.set(attributeName, value);
				updatedEntities.add(originalEntity);
				count++;
			}

			// update all entities
			this.dataService.update(entityName, updatedEntities);
			response.setStatus(HttpServletResponse.SC_OK);
		}
		catch (Exception e)
		{
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			throw e;
		}
	}

	/**
	 * Repeating checks: 1. Checks if EntityCollection Batch Request V2 request is null 2. Checks if the entities max is
	 * exceeded. 3. Existing entity metadata
	 * 
	 * @param request
	 * @param meta
	 * @param entityName
	 * @throws Exception
	 */
	private void generalChecksForBachOperations(@Valid EntityCollectionBatchRequestV2 request, EntityMetaData meta,
			String entityName) throws Exception
	{
		if (request.getEntities().isEmpty())
		{
			throw RestControllerV2.EXCEPTION_NO_ENTITIES;
		}

		if (request.getEntities().size() > MAX_ENTITIES)
		{
			throw RestControllerV2.EXCEPTION_MAX_ENTITIES_EXCEEDED;
		}

		if (meta == null)
		{
			throw RestControllerV2.createUnknownEntityException(entityName);
		}
	}

	/**
	 * Get entity id and perform a check, throwing an MolgenisDataException when necessary
	 * 
	 * @param entity
	 * @param count
	 * @return
	 */
	private String checkForEntityId(Entity entity, int count)
	{
		Object id = entity.getIdValue();
		if (null == id)
		{
			throw RestControllerV2.createMolgenisDataExceptionUnknownIdentifier(count);
		}
		return id.toString();
	}
	
	@ExceptionHandler(MolgenisValidationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public ErrorMessageResponse handleValidationException(MolgenisValidationException e)
	{
		LOG.info("Validation exception occurred.", e);
		return new ErrorMessageResponse(new ErrorMessage(e.getMessage()));
	}

	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(INTERNAL_SERVER_ERROR)
	@ResponseBody
	public ErrorMessageResponse handleRuntimeException(RuntimeException e)
	{
		LOG.error("", e);
		return new ErrorMessageResponse(new ErrorMessage(e.getMessage()));
	}

	private AttributeMetaDataResponseV2 createAttributeMetaDataResponse(String entityName, String attributeName)
	{
		EntityMetaData entity = dataService.getEntityMetaData(entityName);
		if (entity == null)
		{
			throw new UnknownEntityException(entityName + " not found");
		}

		AttributeMetaData attribute = entity.getAttribute(attributeName);
		if (attribute == null)
		{
			throw new RuntimeException("attribute : " + attributeName + " does not exist!");
		}

		AttributeFilter attributeFilter = new AttributeFilter();
		Iterable<AttributeMetaData> attributeParts = attribute.getAttributeParts();

		if (attributeParts != null)
		{
			attributeParts.forEach(attributePart -> attributeFilter.add(attributePart.getName()));
		}

		return new AttributeMetaDataResponseV2(entityName, attribute, attributeFilter, permissionService);
	}

	private EntityCollectionResponseV2 createEntityCollectionResponse(String entityName,
			EntityCollectionRequestV2 request)
	{
		EntityMetaData meta = dataService.getEntityMetaData(entityName);

		Query q = request.getQ() != null ? request.getQ().createQuery(meta) : new QueryImpl();
		q.pageSize(request.getNum()).offset(request.getStart()).sort(request.getSort());

		if (request.getAggs() != null)
		{
			// return aggregates for aggregate query
			AggregateQuery aggsQ = request.getAggs().createAggregateQuery(meta, q);
			if (aggsQ.getAttributeX() == null && aggsQ.getAttributeY() == null)
			{
				throw new MolgenisQueryException("Aggregate query is missing 'x' or 'y' attribute");
			}
			AggregateResult aggs = dataService.aggregate(entityName, aggsQ);
			return new EntityAggregatesResponse(aggs, BASE_URI + '/' + entityName);
		}
		else
		{
			// return entities for query
			Iterable<Entity> it = dataService.findAll(entityName, q);
			Long count = dataService.count(entityName, q);
			EntityPager pager = new EntityPager(request.getStart(), request.getNum(), count, it);

			AttributeFilter attributeFilter = request.getAttrs();
			if (attributeFilter == null)
			{
				attributeFilter = AttributeFilter.ALL_ATTRS_FILTER;
			}
			List<Map<String, Object>> entities = new ArrayList<>();
			createEntitiesValuesResponse(it, meta, attributeFilter, entities);

			return new EntityCollectionResponseV2(pager, entities, attributeFilter, BASE_URI + '/' + entityName, meta,
					permissionService);
		}
	}

	private void createEntitiesValuesResponse(Iterable<Entity> entities, EntityMetaData entityMeta,
			AttributeFilter attrFilter, List<Map<String, Object>> entitiesValues)
	{
		// create value maps, keep track of ref attributes and entities that need to be resolved
		Map<AttributeMetaData, Set<Object>> refEntitiesIds = new HashMap<>();

		for (Entity entity : entities)
		{
			Map<String, Object> entityValues = new LinkedHashMap<>();
			for (AttributeMetaData attr : entityMeta.getAttributes())
			{
				if (attrFilter.includeAttribute(attr))
				{
					createEntityAttrValues(entity, attr, entityValues, refEntitiesIds);
				}
			}
			entitiesValues.add(entityValues);
		}

		// retrieve ref entities
		refEntitiesIds.forEach((refAttr, refEntityIds) -> {
			EntityMetaData refEntityMeta = refAttr.getRefEntity();
			String refEntityName = refEntityMeta.getName();
			Iterable<Entity> refEntities = dataService.findAll(refEntityName, refEntityIds);
			Map<Object, Entity> refEntitiesMap = StreamSupport.stream(refEntities.spliterator(), false)
					.collect(Collectors.toMap(Entity::getIdValue, Function.identity()));

			// update value maps
			String refAttrName = refAttr.getName();
			FieldTypeEnum attrType = refAttr.getDataType().getEnumType();
			entitiesValues.forEach(entityValue -> {
				Object refAttrValue = entityValue.get(refAttrName);
				switch (attrType)
				{
					case CATEGORICAL:
					case XREF:
					case FILE:
						if (refAttrValue != null)
						{
							Entity attrRefEntity = refEntitiesMap.get(refAttrValue);
							List<Map<String, Object>> refEntitiesValues = new ArrayList<>();
							AttributeFilter refAttrFilter = attrFilter.getAttributeFilter(refAttr);
							if (refAttrFilter == null)
							{
								refAttrFilter = createDefaultRefAttributeFilter(refAttr);
							}
							createEntitiesValuesResponse(Arrays.asList(attrRefEntity), refEntityMeta, refAttrFilter,
									refEntitiesValues);
							entityValue.put(refAttrName, refEntitiesValues.get(0));
						}
						break;
					case CATEGORICAL_MREF:
					case MREF:
						if (refAttrValue != null)
						{
							Iterable<Entity> attrRefEntities = ((List<Object>) refAttrValue).stream()
									.map(refEntitiesMap::get).collect(Collectors.toList());
							List<Map<String, Object>> mrefEntitiesValues = new ArrayList<>();
							AttributeFilter refAttrFilter = attrFilter.getAttributeFilter(refAttr);
							if (refAttrFilter == null)
							{
								refAttrFilter = createDefaultRefAttributeFilter(refAttr);
							}
							createEntitiesValuesResponse(attrRefEntities, refEntityMeta, refAttrFilter,
									mrefEntitiesValues);
							entityValue.put(refAttrName, mrefEntitiesValues);
						}
						break;
					// $CASES-OMITTED$
					default:
						throw new RuntimeException("Unknown data type [" + attrType + "]");
				}
			});
		});
	}

	private void createEntityAttrValues(Entity entity, AttributeMetaData attr, Map<String, Object> entityValues,
			Map<AttributeMetaData, Set<Object>> refEntitiesIds)
	{
		String attrName = attr.getName();
		FieldTypeEnum attrType = attr.getDataType().getEnumType();
		switch (attrType)
		{
			case BOOL:
				entityValues.put(attrName, entity.getBoolean(attrName));
				break;
			case CATEGORICAL:
			case XREF:
			case FILE:
				Entity refEntity = entity.getEntity(attrName);
				Object value;
				if (refEntity != null)
				{
					value = refEntity.getIdValue();

					// bookkeeping: keep track of referred entity ids that will be resolved later
					Set<Object> xrefEntityIds = refEntitiesIds.get(attr);
					if (xrefEntityIds == null)
					{
						xrefEntityIds = new HashSet<>();
						refEntitiesIds.put(attr, xrefEntityIds);
					}
					xrefEntityIds.add(value);
				}
				else
				{
					value = null;
				}
				entityValues.put(attrName, value);
				break;
			case CATEGORICAL_MREF:
			case MREF:
				Iterable<Entity> refEntities = entity.getEntities(attrName);
				List<Object> refEntityIds = StreamSupport.stream(refEntities.spliterator(), false)
						.map(Entity::getIdValue).collect(Collectors.toList());
				entityValues.put(attrName, refEntityIds);

				// bookkeeping: keep track of referred entity ids that will be resolved later
				Set<Object> mrefEntityIds = refEntitiesIds.get(attr);
				if (mrefEntityIds == null)
				{
					mrefEntityIds = new HashSet<>();
					refEntitiesIds.put(attr, mrefEntityIds);
				}
				mrefEntityIds.addAll(refEntityIds);
				break;
			case COMPOUND:
				Iterable<AttributeMetaData> attrParts = attr.getAttributeParts();
				attrParts.forEach(attrPart -> createEntityAttrValues(entity, attrPart, entityValues, refEntitiesIds));
				break;
			case DATE:
				Date dateValue = entity.getDate(attrName);
				String dateValueStr = dateValue != null ? getDateFormat().format(dateValue) : null;
				entityValues.put(attrName, dateValueStr);
				break;
			case DATE_TIME:
				Date dateTimeValue = entity.getDate(attrName);
				String dateTimeValueStr = dateTimeValue != null ? getDateTimeFormat().format(dateTimeValue) : null;
				entityValues.put(attrName, dateTimeValueStr);
				break;
			case DECIMAL:
				entityValues.put(attrName, entity.getDouble(attrName));
				break;
			case EMAIL:
			case ENUM:
			case HTML:
			case HYPERLINK:
			case SCRIPT:
			case STRING:
			case TEXT:
				entityValues.put(attrName, entity.getString(attrName));
				break;
			case IMAGE:
				throw new UnsupportedOperationException("Unsupported attribute type [" + attrType + "]");
			case INT:
				entityValues.put(attrName, entity.getInt(attrName));
				break;
			case LONG:
				entityValues.put(attrName, entity.getLong(attrName));
				break;
			default:
				throw new RuntimeException("Unknown data type [" + attrType + "]");
		}
	}

	private Map<String, Object> createEntityResponse(Entity entity, AttributeFilter attrFilter, boolean includeMetaData)
	{
		Map<String, Object> responseData = new LinkedHashMap<String, Object>();
		if (includeMetaData)
		{
			createEntityMetaResponse(entity.getEntityMetaData(), attrFilter, responseData);
		}
		createEntityValuesResponse(entity, attrFilter, responseData);
		return responseData;
	}

	private void createEntityMetaResponse(EntityMetaData entityMetaData, AttributeFilter attrFilter,
			Map<String, Object> responseData)
	{
		responseData.put("_meta", new EntityMetaDataResponseV2(entityMetaData, attrFilter, permissionService));
	}

	private void createEntityValuesResponse(Entity entity, AttributeFilter attrFilter, Map<String, Object> responseData)
	{
		Iterable<AttributeMetaData> attrs = entity.getEntityMetaData().getAttributes();
		attrFilter = attrFilter != null ? attrFilter : AttributeFilter.ALL_ATTRS_FILTER;
		createEntityValuesResponseRec(entity, attrs, attrFilter, responseData);
	}

	private void createEntityValuesResponseRec(Entity entity, Iterable<AttributeMetaData> attrs,
			AttributeFilter attrFilter, Map<String, Object> responseData)
	{
		responseData.put("_href",
				Href.concatEntityHref(BASE_URI, entity.getEntityMetaData().getName(), entity.getIdValue()));
		for (AttributeMetaData attr : attrs)
		{
			String attrName = attr.getName();
			if (attrFilter.includeAttribute(attr))
			{
				FieldTypeEnum dataType = attr.getDataType().getEnumType();
				switch (dataType)
				{
					case BOOL:
						responseData.put(attrName, entity.getBoolean(attrName));
						break;
					case CATEGORICAL:
					case XREF:
					case FILE:
						Entity refEntity = entity.getEntity(attrName);
						Map<String, Object> refEntityResponse;
						if (refEntity != null)
						{
							AttributeFilter refAttrFilter = attrFilter.getAttributeFilter(attr);
							if (refAttrFilter == null)
							{
								refAttrFilter = createDefaultRefAttributeFilter(attr);
							}
							refEntityResponse = createEntityResponse(refEntity, refAttrFilter, false);
						}
						else
						{
							refEntityResponse = null;
						}
						responseData.put(attrName, refEntityResponse);
						break;
					case CATEGORICAL_MREF:
					case MREF:
						Iterable<Entity> refEntities = entity.getEntities(attrName);
						List<Map<String, Object>> refEntityResponses;
						if (refEntities != null)
						{
							refEntityResponses = new ArrayList<Map<String, Object>>();
							AttributeFilter refAttrFilter = attrFilter.getAttributeFilter(attr);
							if (refAttrFilter == null)
							{
								refAttrFilter = createDefaultRefAttributeFilter(attr);
							}
							for (Entity refEntitiesEntity : refEntities)
							{
								refEntityResponses.add(createEntityResponse(refEntitiesEntity, refAttrFilter, false));
							}
						}
						else
						{
							refEntityResponses = null;
						}
						responseData.put(attrName, refEntityResponses);
						break;
					case COMPOUND:
						Iterable<AttributeMetaData> attrParts = attr.getAttributeParts();
						AttributeFilter compoundAttrFilter = new AttributeFilter();
						for (AttributeMetaData attrPart : attrParts)
						{
							compoundAttrFilter.add(attrPart.getName());
						}
						createEntityValuesResponseRec(entity, attrParts, compoundAttrFilter, responseData);
						break;
					case DATE:
						Date dateValue = entity.getDate(attrName);
						String dateValueStr = dateValue != null ? getDateFormat().format(dateValue) : null;
						responseData.put(attrName, dateValueStr);
						break;
					case DATE_TIME:
						Date dateTimeValue = entity.getDate(attrName);
						String dateTimeValueStr = dateTimeValue != null ? getDateTimeFormat().format(dateTimeValue)
								: null;
						responseData.put(attrName, dateTimeValueStr);
						break;
					case DECIMAL:
						responseData.put(attrName, entity.getDouble(attrName));
						break;
					case EMAIL:
					case ENUM:
					case HTML:
					case HYPERLINK:
					case SCRIPT:
					case STRING:
					case TEXT:
						responseData.put(attrName, entity.getString(attrName));
						break;
					case IMAGE:
						throw new UnsupportedOperationException("Unsupported data type [" + dataType + "]");
					case INT:
						responseData.put(attrName, entity.getInt(attrName));
						break;
					case LONG:
						responseData.put(attrName, entity.getLong(attrName));
						break;
					default:
						throw new RuntimeException("Unknown data type [" + dataType + "]");
				}
			}
		}
	}

	static AttributeFilter createDefaultRefAttributeFilter(AttributeMetaData attr)
	{
		EntityMetaData refEntityMeta = attr.getRefEntity();
		String idAttrName = refEntityMeta.getIdAttribute().getName();
		String labelAttrName = refEntityMeta.getLabelAttribute().getName();
		AttributeFilter attrFilter = new AttributeFilter().add(idAttrName).add(labelAttrName);
		if (attr.getDataType().getEnumType() == FieldTypeEnum.FILE)
		{
			attrFilter.add(FileMeta.URL);
		}
		return attrFilter;
	}
}
