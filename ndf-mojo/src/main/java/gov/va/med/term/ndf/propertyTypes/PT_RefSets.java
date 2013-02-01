package gov.va.med.term.ndf.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.PropertyType;

/**
 * Columns from the NDF load which are loaded as attributes / subsets
 * @author Daniel Armbrust
 */
public class PT_RefSets extends PropertyType
{
	public PT_RefSets(String uuidRoot)
	{
		super("RefSets", uuidRoot);
		addProperty("VA_CLASS", "VA Class");
	}
}
