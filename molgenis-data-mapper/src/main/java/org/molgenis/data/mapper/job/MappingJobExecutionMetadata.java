package org.molgenis.data.mapper.job;

import org.molgenis.data.jobs.model.JobExecutionMetaData;
import org.molgenis.data.meta.SystemEntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;
import static org.molgenis.data.mapper.meta.MapperPackage.PACKAGE_MAPPER;
import static org.molgenis.data.meta.AttributeType.BOOL;
import static org.molgenis.data.meta.AttributeType.STRING;
import static org.molgenis.data.meta.model.Package.PACKAGE_SEPARATOR;

@Component
public class MappingJobExecutionMetadata extends SystemEntityType
{
	private static final String SIMPLE_NAME = "MappingJobExecution";
	static final String MAPPING_JOB_EXECUTION = PACKAGE_MAPPER + PACKAGE_SEPARATOR + SIMPLE_NAME;
	static final String MAPPING_JOB_TYPE = "mapping";

	static final String MAPPING_PROJECT_ID = "mappingProjectId";
	static final String TARGET_ENTITY_TYPE_ID = "targetEntityTypeId";
	static final String ADD_SOURCE_ATTRIBUTE = "addSourceAttribute";
	static final String PACKAGE = "package";
	static final String LABEL = "label";

	private final JobExecutionMetaData jobExecutionMetaData;

	@Autowired
	MappingJobExecutionMetadata(JobExecutionMetaData jobExecutionMetaData)
	{
		super(SIMPLE_NAME, PACKAGE_MAPPER);
		this.jobExecutionMetaData = requireNonNull(jobExecutionMetaData);
	}

	@Override
	public void init()
	{
		setLabel("Mapping Job Execution");
		setExtends(jobExecutionMetaData);

		addAttribute(MAPPING_PROJECT_ID).setDataType(STRING).setLabel("Mapping Project ID").setNillable(false);
		addAttribute(TARGET_ENTITY_TYPE_ID).setDataType(STRING).setLabel("Target Entity Type ID").setNillable(false);
		addAttribute(ADD_SOURCE_ATTRIBUTE).setDataType(BOOL).setLabel("Add source attribute").setNillable(false);
		addAttribute(PACKAGE).setDataType(STRING).setLabel("Package").setNillable(false).setDescription(
				"The destination package of the target entity type. Ignored when mapping to an existing entity type.");
		addAttribute(LABEL).setDataType(STRING).setLabel("Label").setNillable(false).setDescription(
				"The label of the target entity type. Ignored when mapping to an existing entity type.");
	}
}