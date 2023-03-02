package io.phasetwo.service.model.jpa;

import static io.phasetwo.service.Orgs.*;

import com.google.common.collect.Streams;
import io.phasetwo.service.model.*;
import io.phasetwo.service.model.jpa.entity.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.*;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.entities.GroupEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

public class OrganizationAdapter implements OrganizationModel, JpaModel<OrganizationEntity> {

  protected final KeycloakSession session;
  protected final OrganizationEntity org;
  protected final EntityManager em;
  protected final RealmModel realm;

  public OrganizationAdapter(
      KeycloakSession session, RealmModel realm, EntityManager em, OrganizationEntity org) {
    this.session = session;
    this.em = em;
    this.org = org;
    this.realm = realm;
  }

  @Override
  public OrganizationEntity getEntity() {
    return org;
  }

  @Override
  public String getId() {
    return org.getId();
  }

  @Override
  public String getName() {
    return org.getName();
  }

  @Override
  public void setName(String name) {
    org.setName(name);
  }

  @Override
  public String getDisplayName() {
    return org.getDisplayName();
  }

  @Override
  public void setDisplayName(String displayName) {
    org.setDisplayName(displayName);
  }

  @Override
  public Set<String> getDomains() {
    return org.getDomains().stream().map(DomainEntity::getDomain).collect(Collectors.toSet());
  }

  @Override
  public void setDomains(Set<String> domains) {
    //  org.setDomains(domains);
    Set<String> lower = domains.stream().map(d -> d.toLowerCase()).collect(Collectors.toSet());
    org.getDomains().removeIf(e -> !lower.contains(e.getDomain()));
    lower.removeIf(d -> org.getDomains().stream().filter(e -> d.equals(e.getDomain())).count() > 0);
    lower.forEach(
        d -> {
          DomainEntity de = new DomainEntity();
          de.setId(KeycloakModelUtils.generateId());
          de.setDomain(d);
          de.setVerified(false);
          de.setOrganization(org);
          org.getDomains().add(de);
        });
  }

  @Override
  public DomainModel getDomain(String domainName) {
    TypedQuery<DomainEntity> query =
        em.createNamedQuery("getDomainByOrganizationAndDomainName", DomainEntity.class);
    query.setParameter("organization", org);
    query.setParameter("search", domainName);
    query.setMaxResults(1);
    try {
      return new DomainAdapter(session, realm, em, query.getSingleResult());
    } catch (Exception e) {
    }
    return null;
  }

  @Override
  public String getUrl() {
    return org.getUrl();
  }

  @Override
  public void setUrl(String url) {
    org.setUrl(url);
  }

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(org.getRealmId());
  }

  @Override
  public UserModel getCreatedBy() {
    return session.users().getUserById(realm, org.getCreatedBy());
  }

  @Override
  public Map<String, List<String>> getAttributes() {
    MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();
    for (OrganizationAttributeEntity attr : org.getAttributes()) {
      result.add(attr.getName(), attr.getValue());
    }
    return result;
  }

  @Override
  public void removeAttribute(String name) {
    org.getAttributes().removeIf(attribute -> attribute.getName().equals(name));
  }

  @Override
  public void removeAttributes() {
    org.getAttributes().clear();
  }

  @Override
  public void setAttribute(String name, List<String> values) {
    removeAttribute(name);
    for (String value : values) {
      OrganizationAttributeEntity a = new OrganizationAttributeEntity();
      a.setId(KeycloakModelUtils.generateId());
      a.setName(name);
      a.setValue(value);
      a.setOrganization(org);
      em.persist(a);
      org.getAttributes().add(a);
    }
  }

  @Override
  public Stream<UserModel> getMembersStream() {
    return org.getMembers().stream()
        .map(OrganizationMemberEntity::getUserId)
        .map(uid -> session.users().getUserById(realm, uid));
  }

  @Override
  public boolean hasMembership(UserModel user) {
    return org.getMembers().stream().anyMatch(m -> m.getUserId().equals(user.getId()));
  }

  @Override
  public void grantMembership(UserModel user) {
    if (hasMembership(user)) return;
    OrganizationMemberEntity m = new OrganizationMemberEntity();
    m.setId(KeycloakModelUtils.generateId());
    m.setUserId(user.getId());
    m.setOrganization(org);
    em.persist(m);
    org.getMembers().add(m);
  }

  @Override
  public void revokeMembership(UserModel user) {
    if (!hasMembership(user)) return;
    org.getMembers().removeIf(m -> m.getUserId().equals(user.getId()));
    getRolesStream().forEach(r -> r.revokeRole(user));
    getGroupsStream().forEach(g -> g.leaveGroup(user));
    if (user.getEmail() != null) revokeInvitations(user.getEmail());
  }

  @Override
  public Stream<InvitationModel> getInvitationsStream() {
    /*
          public List<InvitationEntity> getInvitationsByRealmAndEmail(String realmName, String email) {
      TypedQuery<InvitationEntity> query =
          em.createNamedQuery("getInvitationsByRealmAndEmail", InvitationEntity.class);
      query.setParameter("realmId", realmName);
      query.setParameter("search", email);
      return query.getResultList();
    }
      */
    return org.getInvitations().stream().map(i -> new InvitationAdapter(session, realm, em, i));
  }

  @Override
  public void revokeInvitation(String id) {
    org.getInvitations().removeIf(inv -> inv.getId().equals(id));
  }

  @Override
  public void revokeInvitations(String email) {
    org.getInvitations().removeIf(inv -> inv.getEmail().equals(email.toLowerCase()));
  }

  @Override
  public InvitationModel addInvitation(String email, UserModel inviter) {
    InvitationEntity inv = new InvitationEntity();
    inv.setId(KeycloakModelUtils.generateId());
    inv.setOrganization(org);
    inv.setEmail(email.toLowerCase());
    inv.setInviterId(inviter.getId());
    em.persist(inv);
    org.getInvitations().add(inv);
    return new InvitationAdapter(session, realm, em, inv);
  }

  @Override
  public Stream<OrganizationRoleModel> getRolesStream() {
    return org.getRoles().stream().map(r -> new OrganizationRoleAdapter(session, realm, em, r, this));
  }

  @Override
  public void removeRole(String name) {
    Optional<OrganizationRoleEntity> roleEntityOpt = org.getRoles().stream().filter(r -> r.getName().equals(name)).findFirst();
    if (roleEntityOpt.isEmpty()) return;

    OrganizationRoleEntity roleEntity = roleEntityOpt.get();
    roleEntity.getUserMappings().clear();
    roleEntity.getGroupMappings().clear();
    org.getRoles().remove(roleEntity);
  }

  @Override
  public OrganizationRoleModel addRole(String name) {
    OrganizationRoleEntity r = new OrganizationRoleEntity();
    r.setId(KeycloakModelUtils.generateId());
    r.setName(name);
    r.setOrganization(org);
    em.persist(r);
    org.getRoles().add(r);
    return new OrganizationRoleAdapter(session, realm, em, r, this);
  }

  @Override
  public Stream<OrganizationGroupModel> getGroupsStream() {
    return org.getGroups().stream().map(r -> new OrganizationGroupAdapter(session, realm, em, this, r));
  }

  @Override
  public void removeGroup(String groupId) {
    OrganizationGroupModel group = getGroupById(groupId);
    group.removeGroup();
    org.getGroups().remove(group.getEntity());
  }

  private Stream<OrganizationGroupModel> getAllSiblings(OrganizationGroupModel parent) {
    return parent.getSubGroupsStream().flatMap(g -> {
      Stream<OrganizationGroupModel> subGroupsStream = g.getSubGroupsStream()
              .flatMap(s -> Streams.concat(getAllSiblings(s), Stream.of(s)));
      return Stream.concat(subGroupsStream, Stream.of(g));
    });
  }

  private void checkCycle(OrganizationGroupModel child, OrganizationGroupModel parent) {
    if (parent != null) {
      if (getAllSiblings(child).anyMatch(g -> g.getId().equals(parent.getId()))) {
        throw new ModelIllegalStateException("Cycle detected between groups");
      }
    }
  }

  @Override
  public void moveGroup(OrganizationGroupModel child, OrganizationGroupModel parent) {
    if (parent != null && child.getId().equals(parent.getId())) {
      return;
    }
    checkCycle(child, parent);

    if (child.getParentId() != null) {
      child.getParent().removeChild(child);
    }
    child.setParent(parent);
    if (parent != null) {
      parent.addChild(child);
    }

    em.flush();
  }

  @Override
  public OrganizationGroupModel createGroup(String groupName, OrganizationGroupModel parent) {
    OrganizationGroupEntity g = new OrganizationGroupEntity();
    g.setId(KeycloakModelUtils.generateId());
    g.setName(groupName);
    g.setOrganization(org);
    g.setParentId(parent == null ? GroupEntity.TOP_PARENT_ID : parent.getId());
    em.persist(g);
    org.getGroups().add(g);
    em.flush();
    return new OrganizationGroupAdapter(session, realm, em, this, g);
  }

  @Override
  public Stream<IdentityProviderModel> getIdentityProvidersStream() {
    return getRealm()
        .getIdentityProvidersStream()
        .filter(
            i -> {
              Map<String, String> config = i.getConfig();
              return config != null
                  && config.containsKey(ORG_OWNER_CONFIG_KEY)
                  && getId().equals(config.get(ORG_OWNER_CONFIG_KEY));
            });
  }
}
