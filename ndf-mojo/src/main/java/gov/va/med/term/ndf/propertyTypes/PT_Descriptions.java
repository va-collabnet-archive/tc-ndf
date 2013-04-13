package gov.va.med.term.ndf.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_Descriptions;

public class PT_Descriptions extends BPT_Descriptions
{

	public PT_Descriptions()
	{
		super("NDF");
		addProperty("TRADE", "Trade Name", "Trade name of product", false, SYNONYM + 2);
		addProperty("NF_NAME", "National Formulary name", "National Formulary name, Combine generic an dosage form", false, SYNONYM + 1);
		addProperty("VA_PRODUCT", "VA Product Name", null, false, FSN);
		addProperty("VA_PRN", "VA Print Name", "VA Print Name (name that goes on the prescription label)", false, SYNONYM);
		addProperty("GENERIC", "VA Generic Name", null, false, SYNONYM + 3);
		
		addProperty("VA Category", "VA Category", null, false, FSN);  //from spreadsheet
	}
}
