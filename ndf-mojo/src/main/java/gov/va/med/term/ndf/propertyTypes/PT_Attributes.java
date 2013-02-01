package gov.va.med.term.ndf.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_Attributes;

/**
 * Columns from the NDF load which are loaded as attributes / subsets
 * @author Daniel Armbrust
 */
public class PT_Attributes extends BPT_Attributes
{
	public PT_Attributes(String uuidRoot)
	{
		super(uuidRoot);
		addProperty("NDC_1", "Characters 2-6 of NDF NDC");
		addProperty("NDC_2", "Characters 7-10 of NDF NDC");
		addProperty("NDC_3", "Characters 11-12 of NDF NDC");
		addProperty("NDF_NDC", "NDC as it is in NDF.  1st digit is extra");  //Note, there is not a single thing in the entire DB that is actually unique per row, so nothing qualifies to be a workbench ID
        addProperty("UPN", "Univeral Product Number");
		addProperty("I_DATE_NDC", "Inactive date for either NDC or UPN");
		addProperty("I_DATE_VAP", "Inactive date VA Product Name");
		addProperty("PRODUCT_NU", "Product number for supplies");
		addProperty("FEEDER", "DSS Feeder Key");
		addProperty("PKG_SZ", "Package Size");
		addProperty("PKG_TYPE", "Package Type");
		addProperty("MANUFAC", "Manufacture");
		addProperty("ROA", "Route of administration.  1st one if multiple");
		addProperty("STRENGTH", "Strength of product.  Not entered if product contains more than one ingredient.");
		addProperty("UNITS", "Unit of measure for strength");
		addProperty("DOSE_FORM", "Dosage Form");
		addProperty("CSFS", "Controlled Schedule Federal Schedule");
		addProperty("RX_OTC", "R for Rx, O for OTC");
		addProperty("NF_INDICAT", "National Formulary indicator (YES or NO)");
		addProperty("DISP_UNT", "VA Dispense Unit");
		addProperty("ID", "CMOP ID number.  Each VA Print name has a unique identifier");
		addProperty("MARK", "Mark for CMOP (Mail order).  Yes or No");
	}
}
