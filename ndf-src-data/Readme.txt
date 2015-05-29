Steps to deploy new source content:

	1) Place the source files into the native-source folder
	2) Update the version number as appropriate in pom.xml
	3) Run a command like this to deploy - (maestro is a server name that must be defined with credentials in your maven configuration):
		mvn deploy -DaltDeploymentRepository=maestro::default::https://va.maestrodev.com/archiva/repository/data-files/
		
Note - new source content should not be checked into SVN.  When finished, simply empty the native-source folder.

For NDF - the loader currently expects two files.
	1) - A zip file which contains a single file - a MS Access Database (*.mdb)  (or a *.accdb file)
	2) - The VA Drug Class File - which is an MS Excel Spreadsheet (*.xls)
	
Both of these files are required.  If the distribution format changes, the loader will need to be updated as appropriate. 