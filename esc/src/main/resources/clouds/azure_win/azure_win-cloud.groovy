
cloud {

	// Mandatory. The name of the cloud, as it will appear in the Cloudify UI.
	name = "Azure"
	configuration {

		// Mandatory - Azure IaaS cloud driver.
		className "org.cloudifysource.esc.driver.provisioning.azure.MicrosoftAzureCloudDriver"

		// Optional. The template name for the management machines. Defaults to the first template in the templates section below.
		managementMachineTemplate "MEDIUM_WIN2012"

		// Optional. Indicates whether internal cluster communications should use the machine private IP. Defaults to true.
		connectToPrivateIp true

		// Optional. Path to folder where management state will be written. Null indicates state will not be written.
		// persistentStoragePath persistencePath
	}

	provider {

		// Optional
		provider "azure"

		/*************************************************************************************************************************
		 * Optional. The HTTP/S URL where cloudify can be downloaded from by newly started machines. Defaults to downloading the *
		 * cloudify version matching that of the client from the cloudify CDN.													 *
		 * Change this if your compute nodes do not have access to an internet connection, or if you prefer to use a			 *
		 * different HTTP server instead.																						 *
		************************************************ *************************************************************************/

		// IMPORTANT: the default linux bootstrap script appends '.tar.gz' to the url whereas the default windows script appends '.zip'.
		// Therefore, if setting a custom URL, make sure to leave out the suffix.
		// cloudifyUrl "http://repository.cloudifysource.org/org/cloudifysource/2.7.1-6300-RELEASE/gigaspaces-cloudify-2.7.1-ga-b6300.zip"

    managementGroup "${codeCountry}${codeEnvironment}"
    machineNamePrefix "${codeCountry}${codeEnvironment}"

		managementOnlyFiles ([])

		numberOfManagementMachines 1

		reservedMemoryCapacityPerMachineInMB 1024

		sshLoggingLevel "WARNING"
	}

	user {
		// Azure subscription id
		user subscriptionId
	}

  cloudNetwork {
    custom ([
      /*******************************************************************************************
       * A Virtaul Network Site name.                                                            *
       * All VM's will belong to this network site.                                              *
       * If the specified network site does not exist, it will be created automatically for you. *
       * in this case, you must specify the 'azure.address.space' property                       *
      *******************************************************************************************/
      "azure.networksite.name" : netWorksite,

      /***************************************************************************************
       * CIDR notation specifying the Address Space for your Virtaul Network.                *
       * All VM's will be assigned a private ip from this address space.                     *
      ***************************************************************************************/
      "azure.address.space"    : netAddress,


      /***************************************************************************************
       * Set DNS servers.                                                                    *
      ***************************************************************************************/
      /*
       "azure.dns.servers" : "dnsName1:ipAdress1,dnsName2:ipAdress2"
      */
    ])

    management {
      networkConfiguration {
        subnets ([
          subnet {
            // The name of the subnet.
            name  managementSubnetName
            // CIDR notation specifying the address range for the subnet.
            range adminNetAddress
          }
        ])
      }
    }
    templates ([
      "ADM_NET" : networkConfiguration {
        name null // this property is unused with azure
        subnets {
          subnet {
            name "admin_subnet"
            range "10.0.1.0/24"
          }
        }
      },
      "DATA_NET" :  networkConfiguration {
        name null // this property is unused with azure
        subnets {
          subnet {
            name "data_subnet"
            range "10.0.2.0/23"
          }
        }
      }
    ])
  }

	cloudCompute {

		templates ([

			UBUNTU1410 : computeTemplate{

				imageId "b39f27a8b8c64d52b05eac6a62ebad85__Ubuntu-14_04_1-LTS-amd64-server-20140909-en-us-30GB"
				machineMemoryMB 3500
				hardwareId "Medium"

				username username
				password password

				remoteDirectory "/home/${username}/gs-files"

				localDirectory "upload"

				fileTransfer "SCP"
				remoteExecution "SSH"
				scriptLanguage "LINUX_SHELL"

				//javaUrl "https://s3-eu-west-1.amazonaws.com/cloudify-eu/jdk-6u32-linux-x64.bin"

				custom ([

					// Optional. each availability set represents a different fault domain.
					// "azure.availability.set" : "ENTER_AVAILABILITY_SET",

					// Choose whether do deploy this instance in Staging or Production environment. defaults to Staging
					"azure.deployment.slot": "Staging",

					/**************************************************************
					 * Mandatory only for templates used for management machines. *
					 * Put this file under the path specified in 'localDirectory' *
					***************************************************************/
					"azure.pfx.file": pfxFile,

					// Password that was used to create the certificate
					"azure.pfx.password" : pfxPassword,

          // Endpoints definition
          "azure.endpoints" : [
            [name:"SSH", protocol:"TCP", localPort: "22", port:"22"]
          ]
				])
			},

			LARGE_WIN2012 : computeTemplate{

				imageId "bd507d3a70934695bc2128e3e5a255ba__RightImage-Windows-2012-x64-v13.5"
				machineMemoryMB 7000
				hardwareId "Large"

				username username
				password password

				remoteDirectory "/C\$/Users/${username}/gs-files"

				localDirectory "upload-windows"

				// File transfer mode. Optional, defaults to SCP.
				fileTransfer "CIFS"
				// Remote execution mode. Options, defaults to SSH.
				remoteExecution "WINRM"
				// Script language for remote execution. Defaults to Linux Shell.
				scriptLanguage "WINDOWS_BATCH"

				//javaUrl "https://s3-eu-west-1.amazonaws.com/cloudify-eu/TODO"

				custom ([
          /********************************************************************
           * Optional. Each availability set represents a different fault domain.
           * Availability set code
           * The code will be append to the machineNamePrefix/managementGroup.
           * The cloud service name will resulting to be something like :
           *   ${machineNamePrefix}${availabilityCode}XXX
           * where XXX is an incremental index
          *********************************************************************/
          "azure.availability.set" : "MAS",

					// Choose whether do deploy this instance in Staging or Production environment. defaults to Staging
					"azure.deployment.slot": "Production",

					/**************************************************************
					 * Mandatory only for templates used for management machines. *
					 * Put this file under the path specified in 'localDirectory' *
					***************************************************************/
					"azure.pfx.file": pfxFile,

					// Password that was used to create the certificate
					"azure.pfx.password" : pfxPassword,

					/* Ports to handle for exchanges beetwin windows machines
					   You can avoid this part if you enable the "disable firewall" in bootstrap-management.ps1

  					4174, JINI, for the lookup service (unicast or multicast)
  					6666, HTTP, webster. PU (package of our lifecycle scripts) are downloaded from here
  					7000, LRMI, the deployer (or GSM), the process that deploy PU instances into containers (GSC)
  					7001, LRMI, port of the lookup service (I am not sure it is used...)
  					7002, LRMI, agent that start all other GS process
  					7003, LRMI, orchestrator (or ESM)
  					7010-7110, LRMI, ports of the containers that host "management space", "webui server" and "rest api".
  					7010-7110, LRMI, ports of the management space (that contains shared cloudify attributes).
  					8099, HTTP, webui
  					8100, HTTP, api rest
  					22, TCP, remote execution via ssh and remote copy via scp (if Linux)
  					5985, soap, remote execution via WinRM (if Windows)
  					445, cifs or smb, remote copy via Samba (if Windows)
					*/

					// Endpoints definition
					"azure.endpoints" : [
						[name:"REMOTE_DESKTOP", protocol:"TCP", localPort:"3389", port:"3389"],
						[name:"CIFS_SMB", protocol:"TCP", localPort:"445", port:"445"],
						[name:"WINRM", protocol:"TCP", localPort:"5985", port:"5985"],
						[name:"WINRM_SSL", protocol:"TCP", localPort:"5986", port:"5986"],
						[name:"HTTP", protocol:"TCP", localPort:"80", port:"80"]
					],

					// Firewall port to open (winrm port 5985 should be opened by default on the image)
					"azure.firewall.ports" : [
						[name:"CLOUDIFY_GUI", protocol:"TCP", port:"8099"],
						[name:"CLOUDIFY_REST", protocol:"TCP", port:"8100"],
					]
				])
			},

			MEDIUM_WIN2012 : computeTemplate {

				imageId "bd507d3a70934695bc2128e3e5a255ba__RightImage-Windows-2012-x64-v13.5"

				machineMemoryMB 3500
				hardwareId "Medium"

				username username
				password password

				remoteDirectory "/C\$/Users/${username}/gs-files"

				localDirectory "upload-windows"

				// File transfer mode. Optional, defaults to SCP.
				fileTransfer "CIFS"
				// Remote execution mode. Options, defaults to SSH.
				remoteExecution "WINRM"
				// Script language for remote execution. Defaults to Linux Shell.
				scriptLanguage "WINDOWS_BATCH"

				//javaUrl "https://s3-eu-west-1.amazonaws.com/cloudify-eu/TODO"

				custom ([

					// Optional. each availability set represents a different fault domain.
					// "azure.availability.set" : "ENTER_AVAILABILITY_SET",

					// Choose whether do deploy this instance in Staging or Production environment. defaults to Staging
					"azure.deployment.slot": "Staging",

					/**************************************************************
					 * Mandatory only for templates used for management machines. *
					 * Put this file under the path specified in 'localDirectory' *
					***************************************************************/
					"azure.pfx.file": pfxFile,

					// Password that was used to create the certificate
					"azure.pfx.password" : pfxPassword,

					// Endpoints definition
					"azure.endpoints" : [
            [name:"REMOTE_DESKTOP", protocol:"TCP", localPort:"3389", port:"3389"],
            [name:"CIFS_SMB", protocol:"TCP", localPort:"445", port:"445"],
            [name:"WINRM", protocol:"TCP", localPort:"5985", port:"5985"],
            [name:"WINRM_SSL", protocol:"TCP", localPort:"5986", port:"5986"],
            [name:"HTTP", protocol:"TCP", localPort:"80", port:"80"]
					],

					// Firewall port to open (winrm port 5985 should be opened by default on the image)
					"azure.firewall.ports" : [
					  [name:"CLOUDIFY_GUI", protocol:"TCP", port:"8099"],
					  [name:"CLOUDIFY_REST", protocol:"TCP", port:"8100"]
					]
				])
			}
		])
	}

	custom ([

		/****************************************************************************************
		 * An Affinity Group name.																*
		 * if the specified group does not exist, one will be created automatically for you.	*
		 * in this case, you must specify the 'azure.affinity.location' property				*
		*****************************************************************************************/
		"azure.affinity.group" : affinityGroup,

		/********************************************************************************************************************************
		 * The MS Data Center location. 																								*
		 * All VM's will be launched onto this Data Center. see http://matthew.sorvaag.net/2011/06/windows-azure-data-centre-locations/	*
		 * Mandatory only if the affinity group specifed above is not a pre-existing one.												*
		*********************************************************************************************************************************/
		"azure.affinity.location" : affinityLocation,

		/*****************************************************************************************
		 * A Storage Account name.																 *
		 * All OS Disk Images will be stored in this account. 									 *
		 * If the specified account does not exist, it will be created automatically for you.	 *
		******************************************************************************************/
		"azure.storage.account" : storageAccount,

		/*************************************************************************************************************************
     * Specify whether or not to delete the network (if found) when you execute a teardown command.
		 * If set to 'true', the storage account, affinity group, and network specified above will be deleted upon teardown.	 *
		 * NOTE : if you are using pre exsisting services and you dont want them to be deleted, please set this value to 'false' *
		**************************************************************************************************************************/
		"azure.cleanup.on.teardown" : "true",

    /********************************************************************
		 * Cloud service code
     * The code will be append to the machineNamePrefix/managementGroup.
     * The cloud service name will resulting to be something like :
     *   ${machineNamePrefix}${cloudServiceCode}XXX
     * where XXX is an incremental index
    *********************************************************************/
		"azure.cloud.service.code" : cloudServiceCode,

    // Enable/Disable Cloud Requests Logging.
    "azure.wireLog": "false",
	])
}

