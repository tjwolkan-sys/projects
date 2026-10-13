package com.powerdialer.service;

import com.powerdialer.dao.IServerDao;
import com.powerdialer.dto.ServerFormDTO;
import com.powerdialer.dto.ServerListDTO;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.exceptions.AstServerConnectionException;
import com.powerdialer.model.Server;
import com.powerdialer.model.User;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Location;
import com.powerdialer.model.Tenant;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.ServerValidator;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powerdialer.util.Constants.ROLE_SUPERADMIN;

@Service("serverService")
@Lazy(false)
public class ServerService extends GenericService<Server, ServerFormDTO, ServerListDTO> {

  private final ManagerConnectionComposition connectionComposition;
  private final LocationService locationService;

  private static final Logger LOG = LoggerFactory.getLogger(ServerService.class);

  @Autowired
  public ServerService(IServerDao dao, ManagerConnectionComposition connectionComposition, LocationService locationService) {
    super(dao, Server.class, ServerFormDTO.class, ServerListDTO.class, new ServerValidator());
    this.connectionComposition = connectionComposition;
    this.locationService = locationService;
  }

  @Override
  public ListDetailed<ServerListDTO> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    filters = addCustomFilters(filters);

    ListDetailed<Server> serverList = getDao().getAll(page, sort, filters);

    ListDetailed<ServerListDTO> resultList = new ListDetailed<>();
    List<ServerListDTO> resultListData = getResultListData(serverList.getData());

    resultList.setData(resultListData);
    resultList.setTotalItems(serverList.getTotalItems());

    return resultList;
  }

  private List<ServerListDTO> getResultListData(List<Server> servers) {
    Stream<Server> serverStream = servers.stream()
        .peek(server -> server.setConnectionState(connectionComposition.getManagerConnectionStatus(server.getIp())));

    if (authUtil.isUserInRole(ROLE_SUPERADMIN)) {
      return serverStream
          .map(server -> mapper.map(server, ServerListDTO.class))
          .collect(Collectors.toList());
    }

    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    Set<Tenant> userTenants = user.getTenants();

    List<Location> campaignLocations = locationService.getLocationsWithEnabledCampaigns();

    return serverStream
        .filter(server -> isServerAppropriate(server, userTenants, campaignLocations))
        .map(server -> mapper.map(server, ServerListDTO.class))
        .collect(Collectors.toList());
  }

  private boolean isServerAppropriate(Server server, Set<Tenant> userTenants, List<Location> campaignLocations) {
    if (!serverTenantsMatchAnyUserTenant(server, userTenants)) {
      return false;
    }
    if (server.getType().equals(Constants.SERVER_TYPE.MEDIA) && !serverHasTenants(server)) {
      return false;
    }
    if (!serverIncludedInCampaignLocations(server, campaignLocations)) {
      return false;
    }
    return true;
  }

  private boolean serverHasTenants(Server server) {
    return server.getTenants().size() != 0;
  }

  private boolean serverTenantsMatchAnyUserTenant(Server server, Set<Tenant> userTenants) {
    return server.getTenants().stream()
        .anyMatch(userTenants::contains);
  }

  private boolean serverIncludedInCampaignLocations(Server server, List<Location> campaignLocations) {
    return server.getLocations().stream()
        .anyMatch(campaignLocations::contains);
  }

  public void connect(long id) {
    Server server = getDao().get(id);
    if (Constants.SERVER_TYPE.REGISTRAR != server.getType()) {
      LOG.error("Connection attempt to " + server + "is rejected: it is not the registrar server.");
      throw new AstServerConnectionException("Attempt to connect to non-registrar server using AMI");
    }
    try {
      connectionComposition.openManagerConnection(server);
    } catch (Exception e) {
      LOG.error("Can't connect to server: " + server, e);
      throw new AstServerConnectionException("Cannot connect to " + server, e);
    }
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public void disconnect(String ip) {
    try {
      connectionComposition.stopManagerConnection(ip);
    } catch (Exception e) {
      LOG.error("Cannot disconnect from server with Ip=" + ip, e);
      throw new AstServerConnectionException("Can't disconnect from server with Ip=" + ip, e);
    }
  }
}
