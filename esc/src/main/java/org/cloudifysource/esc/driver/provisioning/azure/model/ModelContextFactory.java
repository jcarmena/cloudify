package org.cloudifysource.esc.driver.provisioning.azure.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * 
 * @author elip
 * 
 */
public final class ModelContextFactory {

	private ModelContextFactory() {

	}

	private static final List<Class<?>> CLASSES = new ArrayList<Class<?>>();

	static {

		CLASSES.add(ConfigurationSet.class);
		CLASSES.add(ConfigurationSets.class);
		CLASSES.add(CreateAffinityGroup.class);
		CLASSES.add(CreateHostedService.class);
		CLASSES.add(CreateStorageServiceInput.class);
		CLASSES.add(Deployment.class);
		CLASSES.add(InputEndpoint.class);
		CLASSES.add(InputEndpoints.class);
		CLASSES.add(LinuxProvisioningConfigurationSet.class);
		CLASSES.add(WindowsProvisioningConfigurationSet.class);
		CLASSES.add(WinRM.class);
		CLASSES.add(Listener.class);
		CLASSES.add(Listeners.class);
		CLASSES.add(NetworkConfigurationSet.class);
		CLASSES.add(OSVirtualHardDisk.class);
		CLASSES.add(Role.class);
		CLASSES.add(RoleList.class);
		CLASSES.add(RestartRoleOperation.class);
		CLASSES.add(HostedServices.class);
		CLASSES.add(HostedService.class);
		CLASSES.add(AffinityGroups.class);
		CLASSES.add(AffinityGroup.class);
		CLASSES.add(RoleInstance.class);
		CLASSES.add(RoleInstanceList.class);
		CLASSES.add(StorageService.class);
		CLASSES.add(StorageServices.class);
		CLASSES.add(Error.class);
		CLASSES.add(Operation.class);
		CLASSES.add(Subnet.class);
		CLASSES.add(Subnets.class);
		CLASSES.add(DnsServerRef.class);
		CLASSES.add(DnsServersRef.class);
		CLASSES.add(DnsServer.class);
		CLASSES.add(DnsServers.class);
		CLASSES.add(Dns.class);
		CLASSES.add(SubnetNames.class);
		CLASSES.add(AddressSpace.class);
		CLASSES.add(GlobalNetworkConfiguration.class);
		CLASSES.add(VirtualNetworkConfiguration.class);
		CLASSES.add(VirtualNetworkSite.class);
		CLASSES.add(VirtualNetworkSites.class);
		CLASSES.add(AttachedTo.class);
		CLASSES.add(Disk.class);
		CLASSES.add(Disks.class);
		CLASSES.add(PersistentVMRole.class);
		CLASSES.add(DataVirtualHardDisk.class);
		CLASSES.add(DomainJoin.class);
		CLASSES.add(JoinCredentials.class);
		CLASSES.add(DataVirtualHardDisks.class);
		CLASSES.add(LocalNetworkSite.class);
		CLASSES.add(LocalNetworkSites.class);
		CLASSES.add(Gateway.class);
		CLASSES.add(LocalNetworkSites.class);
		CLASSES.add(Connection.class);
		CLASSES.add(ConnectionsToLocalNetwork.class);
		CLASSES.add(SharedKey.class);
		CLASSES.add(CreateGatewayParameters.class);
		CLASSES.add(GatewayInfo.class);

	}

	private static Class<?>[] getClasses() {
		Class<?>[] result = new Class<?>[CLASSES.size()];
		CLASSES.toArray(result);
		return result;
	}

	/**
	 * 
	 * @return - a {@link JAXBContext} to be used for marshaling and unmarshalling objects
	 */
	public static synchronized JAXBContext createInstance() {
		JAXBContext context = null;
		try {
			context = JAXBContext.newInstance(getClasses());
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException("Could not create JAXBContext : " + e.getMessage());
		}
		return context;
	}
}
