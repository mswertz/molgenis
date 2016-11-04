package org.molgenis.data.validation.meta;

import junit.framework.Assert;
import org.molgenis.AttributeType;
import org.molgenis.data.DataService;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.data.Sort;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.validation.MolgenisValidationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.mockito.Mockito.*;
import static org.molgenis.AttributeType.*;
import static org.molgenis.data.Sort.Direction.ASC;
import static org.molgenis.data.meta.model.AttributeMetadata.ATTRIBUTE_META_DATA;
import static org.testng.Assert.assertEquals;

public class AttributeValidatorTest
{
	private AttributeValidator attributeValidator;
	private DataService dataService;

	@BeforeMethod
	public void beforeMethod()
	{
		dataService = mock(DataService.class);
		attributeValidator = new AttributeValidator(dataService);
	}

	@Test(expectedExceptions = MolgenisValidationException.class, expectedExceptionsMessageRegExp = "Invalid characters in: \\[invalid.name\\] Only letters \\(a-z, A-Z\\), digits \\(0-9\\), underscores \\(_\\) and hashes \\(#\\) are allowed.")
	public void validateAttributeInvalidName()
	{
		Attribute attr = makeMockAttribute("invalid.name");
		attributeValidator.validate(attr);
	}

	@Test
	public void validateMappedByValidEntity()
	{
		String entityName = "entityName";
		EntityType refEntity = when(mock(EntityType.class).getName()).thenReturn(entityName).getMock();
		Attribute attr = makeMockAttribute("attrName");
		when(attr.getRefEntity()).thenReturn(refEntity);
		String mappedByAttrName = "mappedByAttrName";
		Attribute mappedByAttr = when(mock(Attribute.class).getName()).thenReturn(mappedByAttrName).getMock();
		when(mappedByAttr.getDataType()).thenReturn(XREF);
		when(attr.getMappedBy()).thenReturn(mappedByAttr);
		when(refEntity.getAttribute(mappedByAttrName)).thenReturn(mappedByAttr);
		attributeValidator.validate(attr);
		verify(dataService, times(1)).findOneById(ATTRIBUTE_META_DATA, attr.getIdentifier(), Attribute.class);
	}

	@Test(expectedExceptions = MolgenisDataException.class, expectedExceptionsMessageRegExp = "mappedBy attribute \\[mappedByAttrName\\] is not part of entity \\[entityName\\].")
	public void validateMappedByInvalidEntity()
	{
		String entityName = "entityName";
		EntityType refEntity = when(mock(EntityType.class).getName()).thenReturn(entityName).getMock();
		Attribute attr = makeMockAttribute("attrName");
		when(attr.getRefEntity()).thenReturn(refEntity);
		String mappedByAttrName = "mappedByAttrName";
		Attribute mappedByAttr = when(mock(Attribute.class).getName()).thenReturn(mappedByAttrName).getMock();
		when(mappedByAttr.getDataType()).thenReturn(XREF);
		when(attr.getMappedBy()).thenReturn(mappedByAttr);
		when(refEntity.getAttribute(mappedByAttrName)).thenReturn(null);
		attributeValidator.validate(attr);
		verify(dataService, times(1)).findOneById(ATTRIBUTE_META_DATA, attr.getIdentifier(), Attribute.class);
	}

	@Test(expectedExceptions = MolgenisDataException.class, expectedExceptionsMessageRegExp = "Invalid mappedBy attribute \\[mappedByAttrName\\] data type \\[STRING\\].")
	public void validateMappedByInvalidDataType()
	{
		String entityName = "entityName";
		EntityType refEntity = when(mock(EntityType.class).getName()).thenReturn(entityName).getMock();
		Attribute attr = makeMockAttribute("attrName");
		when(attr.getRefEntity()).thenReturn(refEntity);
		String mappedByAttrName = "mappedByAttrName";
		Attribute mappedByAttr = when(mock(Attribute.class).getName()).thenReturn(mappedByAttrName).getMock();
		when(mappedByAttr.getDataType()).thenReturn(STRING); // invalid type
		when(attr.getMappedBy()).thenReturn(mappedByAttr);
		when(refEntity.getAttribute(mappedByAttrName)).thenReturn(null);
		attributeValidator.validate(attr);
		verify(dataService, times(1)).findOneById(ATTRIBUTE_META_DATA, attr.getIdentifier(), Attribute.class);
	}

	@Test
	public void validateOrderByValid()
	{
		String entityName = "entityName";
		EntityType refEntity = when(mock(EntityType.class).getName()).thenReturn(entityName).getMock();
		Attribute attr = makeMockAttribute("attrName");
		when(attr.getRefEntity()).thenReturn(refEntity);
		String mappedByAttrName = "mappedByAttrName";
		Attribute mappedByAttr = when(mock(Attribute.class).getName()).thenReturn(mappedByAttrName).getMock();
		when(mappedByAttr.getDataType()).thenReturn(XREF);
		when(attr.getMappedBy()).thenReturn(mappedByAttr);
		when(refEntity.getAttribute(mappedByAttrName)).thenReturn(mappedByAttr);
		when(attr.getOrderBy()).thenReturn(new Sort(mappedByAttrName, ASC));
		attributeValidator.validate(attr);
		verify(dataService, times(1)).findOneById(ATTRIBUTE_META_DATA, attr.getIdentifier(), Attribute.class);
	}

	@Test(expectedExceptions = MolgenisDataException.class, expectedExceptionsMessageRegExp = "Unknown entity \\[entityName\\] attribute \\[fail\\] referred to by entity \\[test\\] attribute \\[attrName\\] sortBy \\[fail,ASC\\]")
	public void validateOrderByInvalidRefAttribute()
	{
		String entityName = "entityName";
		EntityType refEntity = when(mock(EntityType.class).getName()).thenReturn(entityName).getMock();
		Attribute attr = makeMockAttribute("attrName");
		EntityType entity = mock(EntityType.class);
		when(entity.getName()).thenReturn("test");
		when(attr.getEntityType()).thenReturn(entity);
		when(attr.getRefEntity()).thenReturn(refEntity);
		String mappedByAttrName = "mappedByAttrName";
		Attribute mappedByAttr = when(mock(Attribute.class).getName()).thenReturn(mappedByAttrName).getMock();
		when(mappedByAttr.getDataType()).thenReturn(XREF);
		when(attr.getMappedBy()).thenReturn(mappedByAttr);
		when(refEntity.getAttribute(mappedByAttrName)).thenReturn(mappedByAttr);
		when(attr.getOrderBy()).thenReturn(new Sort("fail", ASC));
		attributeValidator.validate(attr);
		verify(dataService, times(1)).findOneById(ATTRIBUTE_META_DATA, attr.getIdentifier(), Attribute.class);
	}

	@Test(dataProvider = "disallowedTransitionProvider", expectedExceptions = MolgenisDataException.class)
	public void testDisallowedTransition(Attribute currentAttr, Attribute newAttr)
	{
		when(dataService.findOneById(ATTRIBUTE_META_DATA, newAttr.getIdentifier(), Attribute.class))
				.thenReturn(currentAttr);
		attributeValidator.validate(newAttr);
	}

	@Test(dataProvider = "allowedTransitionProvider")
	public void testAllowedTransition(Attribute currentAttr, Attribute newAttr)
	{
		when(dataService.findOneById(ATTRIBUTE_META_DATA, newAttr.getIdentifier(), Attribute.class))
				.thenReturn(currentAttr);
	}

	@Test
	public void testDefaultValueDate()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("test");
		when(attr.getDataType()).thenReturn(AttributeType.DATE);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getCause().getMessage(), "Unparseable date: \"test\"");
		}
	}

	@Test
	public void testDefaultValueDateValid()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("01-01-2016");
		when(attr.getDataType()).thenReturn(AttributeType.DATE);
		attributeValidator.validateDefaultValue(attr);
	}

	@Test
	public void testDefaultValueDateTime()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("test");
		when(attr.getDataType()).thenReturn(AttributeType.DATE_TIME);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getCause().getMessage(), "Unparseable date: \"test\"");
		}
	}

	@Test
	public void testDefaultValueDateTimeValid()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("2016-10-10T12:00:10+0000");
		when(attr.getDataType()).thenReturn(AttributeType.DATE_TIME);
		attributeValidator.validateDefaultValue(attr);
	}

	@Test
	public void testDefaultValueHyperlink()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("test^");
		when(attr.getDataType()).thenReturn(AttributeType.HYPERLINK);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getMessage(), "Default value [test^] is not a valid hyperlink.");
		}
	}

	@Test
	public void testDefaultValueHyperlinkValid()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("http://www.molgenis.org");
		when(attr.getDataType()).thenReturn(AttributeType.HYPERLINK);
		attributeValidator.validateDefaultValue(attr);
	}

	@Test
	public void testDefaultValueEnum()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("test");
		when(attr.getEnumOptions()).thenReturn(Arrays.asList("a", "b", "c"));
		when(attr.getDataType()).thenReturn(AttributeType.ENUM);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getMessage(),
					"Invalid default value [test] for enum [null] value must be one of [a, b, c]");
		}
	}

	@Test
	public void testDefaultValueEnumValid()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("b");
		when(attr.getEnumOptions()).thenReturn(Arrays.asList("a", "b", "c"));
		when(attr.getDataType()).thenReturn(AttributeType.ENUM);
		attributeValidator.validateDefaultValue(attr);

	}

	@Test
	public void testDefaultValueInt1()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("test");
		when(attr.getDataType()).thenReturn(AttributeType.INT);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getMessage(), "NumberFormatException For input string: \"test\"");
		}
	}

	@Test
	public void testDefaultValueInt2()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("1.0");
		when(attr.getDataType()).thenReturn(AttributeType.INT);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getMessage(), "NumberFormatException For input string: \"1.0\"");
		}
	}

	@Test
	public void testDefaultValueIntValid()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("123456");
		when(attr.getDataType()).thenReturn(AttributeType.INT);
		attributeValidator.validateDefaultValue(attr);
	}

	@Test
	public void testDefaultValueLong()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("test");
		when(attr.getDataType()).thenReturn(AttributeType.LONG);
		try
		{
			attributeValidator.validateDefaultValue(attr);
			Assert.fail();
		}
		catch (MolgenisDataException actual)
		{
			assertEquals(actual.getMessage(), "NumberFormatException For input string: \"test\"");
		}
	}

	@Test
	public void testDefaultValueLongValid()
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getDefaultValue()).thenReturn("123456");
		when(attr.getDataType()).thenReturn(AttributeType.LONG);
		attributeValidator.validateDefaultValue(attr);
	}

	@DataProvider(name = "allowedTransitionProvider")
	private Object[][] allowedTransitionProvider()
	{
		Attribute currentAttr1 = makeMockAttribute("attr1");
		Attribute currentAttr2 = makeMockAttribute("attr2");
		Attribute currentAttr3 = makeMockAttribute("attr3");
		when(currentAttr1.getDataType()).thenReturn(BOOL);
		when(currentAttr2.getDataType()).thenReturn(CATEGORICAL);
		when(currentAttr3.getDataType()).thenReturn(COMPOUND);

		Attribute newAttr1 = makeMockAttribute("attr1");
		Attribute newAttr2 = makeMockAttribute("attr2");
		Attribute newAttr3 = makeMockAttribute("attr3");
		when(newAttr1.getDataType()).thenReturn(INT);
		when(newAttr2.getDataType()).thenReturn(INT);
		when(newAttr3.getDataType()).thenReturn(INT);

		return new Object[][] { { currentAttr1, newAttr1 }, { currentAttr2, newAttr2 }, { currentAttr3, newAttr3 } };
	}

	@DataProvider(name = "disallowedTransitionProvider")
	private Object[][] disallowedTransitionProvider()
	{
		Attribute currentAttr1 = makeMockAttribute("attr1");
		Attribute currentAttr2 = makeMockAttribute("attr2");
		Attribute currentAttr3 = makeMockAttribute("attr3");
		when(currentAttr1.getDataType()).thenReturn(BOOL);
		when(currentAttr2.getDataType()).thenReturn(CATEGORICAL);
		when(currentAttr3.getDataType()).thenReturn(COMPOUND);

		Attribute newAttr1 = makeMockAttribute("attr1");
		Attribute newAttr2 = makeMockAttribute("attr2");
		Attribute newAttr3 = makeMockAttribute("attr3");
		when(newAttr1.getDataType()).thenReturn(ONE_TO_MANY);
		when(newAttr2.getDataType()).thenReturn(HYPERLINK);
		when(newAttr3.getDataType()).thenReturn(FILE);

		return new Object[][] { { currentAttr1, newAttr1 }, { currentAttr2, newAttr2 }, { currentAttr3, newAttr3 } };
	}

	private Attribute makeMockAttribute(String name)
	{
		Attribute attr = mock(Attribute.class);
		when(attr.getName()).thenReturn(name);
		when(attr.getIdentifier()).thenReturn(name);
		return attr;
	}
}