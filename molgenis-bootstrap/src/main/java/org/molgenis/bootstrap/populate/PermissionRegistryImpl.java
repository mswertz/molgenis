package org.molgenis.bootstrap.populate;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.molgenis.core.ui.admin.user.UserAccountController;
import org.molgenis.data.DataService;
import org.molgenis.data.meta.UploadPackage;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.meta.model.Package;
import org.molgenis.data.meta.model.PackageMetadata;
import org.molgenis.data.plugin.model.PluginIdentity;
import org.molgenis.data.plugin.model.PluginPermission;
import org.molgenis.data.security.*;
import org.molgenis.data.security.auth.Role;
import org.molgenis.util.Pair;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.molgenis.data.decorator.meta.DecoratorConfigurationMetadata.DECORATOR_CONFIGURATION;
import static org.molgenis.data.file.model.FileMetaMetaData.FILE_META;
import static org.molgenis.data.i18n.model.L10nStringMetaData.L10N_STRING;
import static org.molgenis.data.i18n.model.LanguageMetadata.LANGUAGE;
import static org.molgenis.data.meta.model.AttributeMetadata.ATTRIBUTE_META_DATA;
import static org.molgenis.data.meta.model.EntityTypeMetadata.ENTITY_TYPE_META_DATA;
import static org.molgenis.data.meta.model.PackageMetadata.PACKAGE;
import static org.molgenis.data.meta.model.TagMetadata.TAG;
import static org.molgenis.data.security.auth.RoleMetadata.ROLE;
import static org.molgenis.data.security.auth.RoleMetadata.NAME;
import static org.molgenis.security.account.AccountService.ALL_USER_GROUP;
import static org.molgenis.security.acl.SidUtils.createSid;

@Component
public class PermissionRegistryImpl implements PermissionRegistry
{
	private final DataService dataService;

	public PermissionRegistryImpl(DataService dataService)
	{
		this.dataService = requireNonNull(dataService);
	}

	@Override
	public Multimap<ObjectIdentity, Pair<Permission, Sid>> getPermissions()
	{
		ImmutableMultimap.Builder<ObjectIdentity, Pair<Permission, Sid>> mapBuilder = new ImmutableMultimap.Builder<>();

		Role userRole = dataService.query(ROLE, Role.class).eq(NAME, ALL_USER_GROUP).findOne();
		Sid allUsersGroupSid = createSid(userRole);

		ObjectIdentity pluginIdentity = new PluginIdentity(UserAccountController.ID);
		mapBuilder.putAll(pluginIdentity, new Pair<>(PluginPermission.READ, allUsersGroupSid));

		dataService.findAll(ENTITY_TYPE_META_DATA,
				Stream.of(ENTITY_TYPE_META_DATA, ATTRIBUTE_META_DATA, PACKAGE, TAG, LANGUAGE, L10N_STRING, FILE_META,
						DECORATOR_CONFIGURATION), EntityType.class).forEach(entityType ->
		{
			ObjectIdentity entityTypeIdentity = new EntityTypeIdentity(entityType);
			Permission entityTypePermissions = EntityTypePermissionUtils.getCumulativePermission(
					EntityTypePermission.READ);
			mapBuilder.putAll(entityTypeIdentity, new Pair<>(entityTypePermissions, allUsersGroupSid));
		});

		dataService.findAll(PackageMetadata.PACKAGE, Stream.of(UploadPackage.UPLOAD), Package.class).forEach(pack ->
		{
			ObjectIdentity packageIdentity = new PackageIdentity(pack);
			Permission packagePermissions = PackagePermissionUtils.getCumulativePermission(PackagePermission.WRITEMETA);
			mapBuilder.putAll(packageIdentity, new Pair<>(packagePermissions, allUsersGroupSid));
		});

		return mapBuilder.build();
	}
}
