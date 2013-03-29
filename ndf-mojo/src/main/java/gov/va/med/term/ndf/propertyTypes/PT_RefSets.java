package gov.va.med.term.ndf.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_Refsets;
import java.util.UUID;

/**
 * Columns from the NDF load which are loaded as attributes / subsets
 * @author Daniel Armbrust
 */
public class PT_RefSets extends BPT_Refsets
{
	public PT_RefSets(String uuidRoot, UUID refsetIdentityParent)
	{
		super(uuidRoot, refsetIdentityParent);
		addProperty("VA_CLASS", "VA Class");
	}
}
