package gov.va.med.term.ndf.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_IDs;

/**
 * Columns from the NDF load which are loaded as attributes / subsets
 * @author Daniel Armbrust
 */
public class PT_IDs extends BPT_IDs
{
	public PT_IDs(String uuidRoot)
	{
		super(uuidRoot);
		//Note, there is not a single thing in the entire DB that is actually unique per row, so nothing qualifies to be a workbench ID in version 1.  
		//In version 2,, NDF_NDC and FEEDER are unique, so we move them over to the ID type attributes.
		addProperty("NDF_NDC", "NDF NDC", "NDC as it is in NDF.  1st digit is extra", 2, 0, false);  
		addProperty("FEEDER", "DSS Feeder Key", null, 2, 0, false);
	}
}
