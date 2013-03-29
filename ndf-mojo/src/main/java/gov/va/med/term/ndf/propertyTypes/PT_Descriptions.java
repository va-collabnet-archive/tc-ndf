package gov.va.med.term.ndf.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_Descriptions;

public class PT_Descriptions extends BPT_Descriptions
{

	public PT_Descriptions(String uuidRoot)
	{
		super(uuidRoot);
		addProperty("TRADE", "Trade name of product");
		addProperty("NF_NAME", "National Formulary name, Combine generic an dosage form");
		addProperty("VA_PRODUCT", "VA Product Name");
		addProperty("VA_PRN", "VA Print Name (name that goes on the prescription label)");
	}
}
