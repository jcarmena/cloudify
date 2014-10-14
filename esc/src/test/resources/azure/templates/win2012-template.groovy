[

	win2012 : computeTemplate {

		imageId "a699494373c04fc0bc8f2bb1389d6106__Windows-Server-2012-R2-201408.01-en.us-127GB.vhd"

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

			"azure.deployment.slot": "Staging",

			"azure.pfx.file": pfxFile,

			// Password that was used to create the certificate
			"azure.pfx.password" : pfxPassword,

			// Endpoints definition
			"azure.endpoints" : [
				[name:"CIFS_SMB", protocol:"TCP", port:"445"],
				[name:"WINRM", protocol:"TCP", port:"5985"],
				[name:"WINRM_SSL", protocol:"TCP", port:"5986"],
				[name:"HTTP", protocol:"TCP", port:"80"]
			],

//			"azure.firewall.ports" : [
//				  [name:"CLOUDIFY_GUI", protocol:"TCP", port:"8099"],
//				  [name:"CLOUDIFY_REST", protocol:"TCP", port:"8100"]
//			]
		])
	}
]
