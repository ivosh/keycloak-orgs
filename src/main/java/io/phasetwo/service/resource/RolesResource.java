package io.phasetwo.service.resource;

import static io.phasetwo.service.resource.Converters.*;
import static io.phasetwo.service.resource.OrganizationResourceType.*;

import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationRoleModel;
import io.phasetwo.service.representation.OrganizationRole;
import java.util.stream.Stream;
import javax.validation.constraints.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.admin.OperationType;

@JBossLog
public class RolesResource extends OrganizationAdminResource {

  private final OrganizationModel organization;

  public RolesResource(OrganizationAdminResource parent, OrganizationModel organization) {
    super(parent);
    this.organization = organization;
  }

  @Path("{alias}")
  public RoleResource roles(@PathParam("alias") String name) {
    if (organization.getRoleByName(name) == null) {
      throw new NotFoundException();
    }
    return new RoleResource(this, organization, name);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<OrganizationRole> getRoles() {
    return organization.getRolesStream().map(r -> convertOrganizationRole(r));
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createRole(OrganizationRole representation) {
    if (!auth.hasManageOrgs() && !auth.hasOrgManageRoles(organization)) {
      throw notAuthorized(OrganizationAdminAuth.ORG_ROLE_MANAGE_ROLES, organization);
    }
    OrganizationRoleModel r = organization.getRoleByName(representation.getName());
    if (r != null) {
      log.debug("duplicate role");
      throw new ClientErrorException(Response.Status.CONFLICT);
    }
    r = organization.addRole(representation.getName());
    r.setDescription(representation.getDescription());

    OrganizationRole or = convertOrganizationRole(r);
    adminEvent
        .resource(ORGANIZATION_ROLE.name())
        .operation(OperationType.CREATE)
        .resourcePath(session.getContext().getUri(), or.getName())
        .representation(or)
        .success();

    return Response.created(
            session.getContext().getUri().getAbsolutePathBuilder().path(or.getName()).build())
        .build();
  }
}
