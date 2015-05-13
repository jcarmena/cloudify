package org.cloudifysource.esc.driver.provisioning.storage.azure;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.cloudifysource.domain.cloud.Cloud;
import org.cloudifysource.esc.driver.provisioning.CloudProvisioningException;
import org.cloudifysource.esc.driver.provisioning.ProvisioningContext;
import org.cloudifysource.esc.driver.provisioning.azure.MicrosoftAzureCloudDriver;
import org.cloudifysource.esc.driver.provisioning.azure.client.MicrosoftAzureException;
import org.cloudifysource.esc.driver.provisioning.azure.client.MicrosoftAzureRestClient;
import org.cloudifysource.esc.driver.provisioning.azure.client.UUIDHelper;
import org.cloudifysource.esc.driver.provisioning.azure.model.DataVirtualHardDisk;
import org.cloudifysource.esc.driver.provisioning.azure.model.Deployment;
import org.cloudifysource.esc.driver.provisioning.azure.model.Role;
import org.cloudifysource.esc.driver.provisioning.azure.model.RoleInstance;
import org.cloudifysource.esc.driver.provisioning.storage.AzureStorageProvisioningDriver;
import org.cloudifysource.esc.driver.provisioning.storage.StorageProvisioningException;

public class AzureStorageProvisioningDriverImpl implements AzureStorageProvisioningDriver {

	private static final Logger logger = Logger.getLogger(AzureStorageProvisioningDriverImpl.class.getName());

	private static final String HOSTCACHING_NONE = "None";
	private static final String HOSTCACHING_READ_WRITE = "ReadWrite";
	private static final String HOSTCACHING_READ_ONLY = "ReadOnly";
	private static final long STORAGE_SLEEP_TIMEOUT = 30000L;
	private static final int LUN_MIN = 1;
	private static final int LUN_MAX = 15;

	private MicrosoftAzureCloudDriver computeDriver;

	private String affinityGroup;

	@Override
	public void setConfig(Cloud cloud, String computeTemplateName) {
		this.affinityGroup = (String) cloud.getCustom().get(MicrosoftAzureCloudDriver.AZURE_AFFINITY_GROUP);
		if (affinityGroup == null) {
			throw new IllegalArgumentException("Custom field '" + MicrosoftAzureCloudDriver.AZURE_AFFINITY_GROUP
					+ "' must be set");
		}
		logger.info("Initializing azure storage driver with affinity group: " + affinityGroup);
	}

	private AzureDeploymentContext getAzureContext() {
		return computeDriver.getAzureContext();
	}

	private MicrosoftAzureRestClient getAzureClient() {
		return computeDriver.getAzureContext().getAzureClient();
	}

	@Override
	public void setComputeContext(Object computeContext) throws StorageProvisioningException {
		if (computeContext != null) {
			if (!(computeContext instanceof MicrosoftAzureCloudDriver)) {
				throw new StorageProvisioningException("Incompatible context class "
						+ computeContext.getClass().getName());
			}
			this.computeDriver = (MicrosoftAzureCloudDriver) computeContext;
		}
		logger.info("Initialize Azure storage driver with context: " + computeDriver.getAzureContext());
	}

	@Override
	public void onMachineFailure(final ProvisioningContext context, final String templateName, final long duration,
			final TimeUnit unit) throws TimeoutException, CloudProvisioningException, StorageProvisioningException {
		String machineId = context.getPreviousMachineDetails().getMachineId();
		logger.warning("Machine id '" + machineId
				+ "'has failed and might have create volumes which are not cleaned propertly");
	}

	@Override
	public void close() {

	}

	private String getThreadId() {
		return "[" + Thread.currentThread().getId() + "] ";
	}

	@Override
	public void createStorageAccount(String storageAccountName, long duration, TimeUnit timeUnit)
			throws StorageProvisioningException, TimeoutException {

		long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

		boolean created = false;
		while (!created) {
			if (System.currentTimeMillis() > endTime) {
				throw new TimeoutException(getThreadId() + "Timeout creating the storage account " + storageAccountName);
			}
			try {
				logger.info(getThreadId() + "Creating Azure storage " + storageAccountName);
				getAzureClient().createStorageAccount(affinityGroup, storageAccountName, endTime);
				created = true;
			} catch (Exception e) {
				try {
					logger.log(Level.WARNING, getThreadId() + "Error creating the storage account '"
							+ storageAccountName + "'. Sleeping " + STORAGE_SLEEP_TIMEOUT
							+ " ms before reattempt", e.getMessage());
					Thread.sleep(STORAGE_SLEEP_TIMEOUT);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					logger.warning(getThreadId() + "Sleep interrupted");
				}
			}
		}
	}

	@Override
	public String createDataDisk(String storageAccountName, String ipAddress, int diskSize, int lun,
			String hostCachingValue, long duration, TimeUnit timeUnit) throws StorageProvisioningException,
			TimeoutException {

		this.validateLun(lun);
		final String hostCaching = this.handleHostCachingValue(hostCachingValue);

		long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

		logger.info(String.format("%sCreating a new data disk in storage account '%s' on '%s'", getThreadId(),
				storageAccountName, ipAddress));

		String dataDiskName = null;
		AzureDeploymentContext context = getAzureContext();
		MicrosoftAzureRestClient azureClient = getAzureClient();
		try {

			// Get the deployment to retrieve the role name
			String deploymentName = context.getDeploymentName();
			String cloudServiceName = context.getCloudServiceName();
			Deployment deployment = azureClient.getDeploymentByName(cloudServiceName, deploymentName);

			RoleInstance roleInstance = deployment.getRoleInstanceList().getRoleInstanceByIpAddress(ipAddress);
			if (roleInstance == null) {
				throw new StorageProvisioningException(String.format(
						"%sCouldn't find a role in (ip=%s, cloudService=%s, deploymentName=%s)",
						getThreadId(), ipAddress, cloudServiceName, deploymentName));
			}

			String roleName = roleInstance.getInstanceName();
			Role role = deployment.getRoleList().getRoleByName(roleName);
			DataVirtualHardDisk attachedDisk = role.getAttachedDataDiskByLun(lun);

			// a data disk is already attached with this LUN ?
			if (attachedDisk != null) {
				throw new StorageProvisioningException(
						String.format(
								"%sa datadisk at LUN %d is already attached to role %s (ip=%s, cloudService=%s, deploymentName=%s)",
								getThreadId(), lun, roleName, ipAddress, cloudServiceName, deploymentName));
			}

			logger.info(String.format("%sCreating data disk in storage account %s for %s on lun %s", getThreadId(),
					storageAccountName, roleName, lun));

			// Generate the vhd filename
			StringBuilder vhdFilename = new StringBuilder();
			vhdFilename.append(roleName.toLowerCase());
			vhdFilename.append("_data");
			vhdFilename.append(String.format("%02d", lun));

			azureClient.addDataDiskToVM(cloudServiceName, deploymentName, roleName, storageAccountName,
					vhdFilename.toString(), diskSize, lun, hostCaching, endTime);
			attachedDisk = azureClient.getDataDisk(cloudServiceName, deploymentName, roleName,
					lun, endTime);

			dataDiskName = attachedDisk.getDiskName();

		} catch (MicrosoftAzureException e) {
			throw new StorageProvisioningException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new StorageProvisioningException(e);
		}

		return dataDiskName;
	}

	private void validateLun(int lun) throws StorageProvisioningException {
		if (lun < LUN_MIN || lun > LUN_MAX) {
			String invalidLun =
					String.format("The Lun number '%d' is invalid, it should be between %d and %d", lun, LUN_MIN,
							LUN_MAX);
			logger.severe(invalidLun);
			throw new StorageProvisioningException(invalidLun);
		}

	}

	private String handleHostCachingValue(String hostCaching) {
		hostCaching = StringUtils.isBlank(hostCaching) ? HOSTCACHING_NONE : hostCaching.trim();
		if (StringUtils.equalsIgnoreCase(hostCaching, HOSTCACHING_READ_ONLY)) {
			hostCaching = HOSTCACHING_READ_ONLY;
		} else if (StringUtils.equalsIgnoreCase(hostCaching, HOSTCACHING_READ_WRITE)) {
			hostCaching = HOSTCACHING_READ_WRITE;
		} else {
			if (!StringUtils.equalsIgnoreCase(hostCaching, HOSTCACHING_NONE)) {
				logger.warning(getThreadId() + "Unknown host caching value " + hostCaching + ". Using default ("
						+ HOSTCACHING_NONE
						+ ")");
			}
			hostCaching = HOSTCACHING_NONE;
		}
		return hostCaching;
	}

	@Override
	public void deleteDataDisk(String diskName, long duration, TimeUnit timeUnit) throws StorageProvisioningException,
			TimeoutException {
		logger.info(getThreadId() + "Deleting datadisk " + diskName);
		long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);
		try {
			getAzureClient().deleteDisk(diskName, true, endTime);
		} catch (MicrosoftAzureException e) {
			throw new StorageProvisioningException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new StorageProvisioningException(e);
		}
	}

	@Override
	public void attachDataDisk(String diskName, String ipAddress, int lun, long duration, TimeUnit timeUnit)
			throws StorageProvisioningException,
			TimeoutException {
		long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

		MicrosoftAzureRestClient azureClient = getAzureClient();
		AzureDeploymentContext context = getAzureContext();

		String cloudServiceName = context.getCloudServiceName();
		String deploymentName = context.getDeploymentName();
		String roleName = null;

		try {
			// Get the deployment to retrieve the role name
			Deployment deployment = azureClient.getDeploymentByName(cloudServiceName, deploymentName);
			for (RoleInstance role : deployment.getRoleInstanceList().getRoleInstances()) {
				if (role.getIpAddress().equals(ipAddress)) {
					roleName = role.getRoleName();
				}
			}
			if (roleName == null) {
				throw new StorageProvisioningException(String.format(
						"%sCouldn't find role with ip address %s (cloudService=%s, deploymentName=%s)",
						getThreadId(), ipAddress, cloudServiceName, deploymentName));
			}

			logger.info(String.format("%sAttaching data disk %s to %s on lun %d", getThreadId(), diskName, roleName,
					lun));

			// Attach the existing data disk to the VM
			azureClient.addExistingDataDiskToVM(cloudServiceName, deploymentName, roleName,
					diskName, lun, endTime);
		} catch (MicrosoftAzureException e) {
			throw new StorageProvisioningException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new StorageProvisioningException(e);
		}
	}

	@Override
	public void detachDataDisk(String diskName, String ipAddress, long duration, TimeUnit timeUnit)
			throws StorageProvisioningException, TimeoutException {

		long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

		MicrosoftAzureRestClient azureClient = getAzureClient();
		AzureDeploymentContext context = getAzureContext();

		String cloudServiceName = context.getCloudServiceName();
		String deploymentName = context.getDeploymentName();
		String roleName = null;
		int lun = -1;

		try {
			// Get the deployment to retrieve the role name
			Deployment deployment = azureClient.getDeploymentByName(cloudServiceName, deploymentName);
			RoleInstance roleInstance = deployment.getRoleInstanceList().getRoleInstanceByIpAddress(ipAddress);
			if (roleInstance == null) {
				throw new StorageProvisioningException(String.format(
						"%sCouldn't find role with ip address %s (cloudService=%s, deploymentName=%s)",
						getThreadId(), ipAddress, cloudServiceName, deploymentName));

			}

			roleName = roleInstance.getInstanceName();
			if (roleName == null) {
				throw new StorageProvisioningException(String.format(
						"%sCouldn't find role with ip address %s (cloudService=%s, deploymentName=%s)",
						getThreadId(), ipAddress, cloudServiceName, deploymentName));
			}

			Role role = deployment.getRoleList().getRoleByName(roleName);

			// Retrieve the LUN number from the data disk name
			DataVirtualHardDisk attachedDisk = role.getAttachedDataDiskByName(diskName);
			if (attachedDisk == null) {

				throw new StorageProvisioningException(
						String.format(
								"%sCouldn't find Lun number for dataDisk %s for VM Role with IP address %s (cloudService=%s, deploymentName=%s)",
								getThreadId(), diskName, ipAddress, cloudServiceName, deploymentName));

			}

			lun = attachedDisk.getLun();

			// Detach the data disk from the VM
			azureClient.removeDataDisk(cloudServiceName, deploymentName, roleName, lun, endTime);

			// set a new label name for the detached disk
			String newLabel = "unattached-data-disk" + UUIDHelper.generateRandomUUID(4);

			azureClient.updateDataDiskLabel(diskName, newLabel, endTime);

		} catch (MicrosoftAzureException e) {
			throw new StorageProvisioningException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new StorageProvisioningException(e);
		}
	}

	@Override
	public void deleteStorageAccount(String storageAccountName, long duration, TimeUnit timeUnit)
			throws StorageProvisioningException, TimeoutException {

		long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

		boolean deleted = false;
		while (!deleted) {
			if (System.currentTimeMillis() > endTime) {
				throw new TimeoutException(getThreadId() + "Timeout creating the storage account " + storageAccountName);
			}
			try {
				getAzureClient().deleteStorageAccount(storageAccountName, endTime);
				deleted = true;

			} catch (Exception e) {
				try {
					logger.log(Level.WARNING, getThreadId() + "Error deleting the storage account '"
							+ storageAccountName + "'. Sleeping " + STORAGE_SLEEP_TIMEOUT
							+ " ms before reattempt", e.getMessage());
					Thread.sleep(STORAGE_SLEEP_TIMEOUT);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					logger.warning(getThreadId() + "Sleep interrupted");
				}
			}
		}

	}

}