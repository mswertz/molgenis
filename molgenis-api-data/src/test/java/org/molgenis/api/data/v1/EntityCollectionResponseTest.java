package org.molgenis.api.data.v1;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Collections;
import org.molgenis.data.DataService;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.security.core.UserPermissionEvaluator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class EntityCollectionResponseTest {
  private EntityType entityType;
  private UserPermissionEvaluator permissionService;
  private DataService dataService;

  @BeforeMethod
  public void setUp() {
    entityType = when(mock(EntityType.class).getId()).thenReturn("entityTypeId").getMock();
    when(entityType.getAttributes()).thenReturn(Collections.emptyList());
    permissionService = mock(UserPermissionEvaluator.class);
    dataService = mock(DataService.class);

    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  public void getNextHref() {
    EntityPager pager = new EntityPager(0, 10, 25L, null);
    EntityCollectionResponse response =
        new EntityCollectionResponse(
            pager, null, "/test", entityType, permissionService, dataService);
    assertEquals(response.getNextHref(), "/test?start=10&num=10");

    pager = new EntityPager(10, 10, 25L, null);
    response =
        new EntityCollectionResponse(
            pager, null, "/test", entityType, permissionService, dataService);
    assertEquals(response.getNextHref(), "/test?start=20&num=10");

    pager = new EntityPager(0, 25, 25L, null);
    response =
        new EntityCollectionResponse(
            pager, null, "/test", entityType, permissionService, dataService);
    assertNull(response.getNextHref());
  }

  @Test
  public void getPrevHref() {
    EntityPager pager = new EntityPager(0, 15, 30L, null);
    EntityCollectionResponse response =
        new EntityCollectionResponse(
            pager, null, "/test", entityType, permissionService, dataService);
    assertNull(response.getPrevHref());

    pager = new EntityPager(15, 15, 30L, null);
    response =
        new EntityCollectionResponse(
            pager, null, "/test", entityType, permissionService, dataService);
    assertEquals(response.getPrevHref(), "/test?start=0&num=15");

    pager = new EntityPager(30, 15, 30L, null);
    response =
        new EntityCollectionResponse(
            pager, null, "/test", entityType, permissionService, dataService);
    assertEquals(response.getPrevHref(), "/test?start=15&num=15");
  }
}
