package gov.va.med.term.ndf.mojo;

import gov.va.med.term.ndf.propertyTypes.PT_Attributes;
import gov.va.med.term.ndf.propertyTypes.PT_ContentVersion;
import gov.va.med.term.ndf.propertyTypes.PT_ContentVersion.ContentVersion;
import gov.va.med.term.ndf.propertyTypes.PT_Descriptions;
import gov.va.med.term.ndf.propertyTypes.PT_IDs;
import gov.va.med.term.ndf.propertyTypes.PT_RefSets;
import gov.va.med.term.ndf.util.AlphanumComparator;
import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import gov.va.oia.terminology.converters.sharedUtils.EConceptUtility;
import gov.va.oia.terminology.converters.sharedUtils.EConceptUtility.DescriptionType;
import gov.va.oia.terminology.converters.sharedUtils.Unzip;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.Property;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.PropertyType;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.ValuePropertyPair;
import gov.va.oia.terminology.converters.sharedUtils.stats.ConverterUUID;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.dwfa.cement.ArchitectonicAuxiliary;
import org.ihtsdo.etypes.EConcept;
import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

/**
 * Goal which converts NDF data into the workbench jbin format
 * 
 * @goal convert-NDF-data
 * 
 * @phase process-sources
 */
public class NDFImportMojo extends AbstractMojo
{
	private final String ndfNamespaceBaseSeed = "gov.va.med.term.ndf";

	private EConceptUtility eConceptUtil_;
	private DataOutputStream dos;

	/**
	 * Where to put the output file.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */

	private File outputDirectory;

	/**
	 * Location of source data files. Expected to be a directory.
	 * 
	 * @parameter
	 * @required
	 */
	private File inputFile;

	/**
	 * Loader version number
	 * Use parent because project.version pulls in the version of the data file, which I don't want.
	 * 
	 * @parameter expression="${project.parent.version}"
	 * @required
	 */
	private String loaderVersion;

	/**
	 * Content version number
	 * 
	 * @parameter expression="${project.version}"
	 * @required
	 */
	private String releaseVersion;

	public void execute() throws MojoExecutionException
	{
		try
		{
			if (!outputDirectory.exists())
			{
				outputDirectory.mkdirs();
			}
			
			File touch = new File(outputDirectory, "ndfEConcepts.jbin");
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(touch)));

			eConceptUtil_ = new EConceptUtility(ndfNamespaceBaseSeed, "NDF Path", dos);
			
			int version = -1;
			if (releaseVersion.startsWith("2012-08"))
			{
				version = 1;  
			}
			else if (releaseVersion.startsWith("2013-02"))
			{
				version = 2;
			}
			else if (releaseVersion.startsWith("2013-08-28"))
			{
				version = 2;
			}
			else
			{
				ConsoleUtil.printErrorln("Untested source version.  Using newest known properties map");
				version = 2;
			}
			
			PropertyType.setSourceVersion(version);
			
			PropertyType attributes = new PT_Attributes();
			PT_ContentVersion contentVersion = new PT_ContentVersion();
			PropertyType descriptions = new PT_Descriptions();
			PropertyType ids = new PT_IDs();
			PT_RefSets refsets = new PT_RefSets();

			EConcept metaDataRoot = eConceptUtil_.createConcept("NDF Metadata", ArchitectonicAuxiliary.Concept.ARCHITECTONIC_ROOT_CONCEPT.getPrimoridalUid());
			metaDataRoot.writeExternal(dos);

			List<PropertyType> allPropertyTypes = new ArrayList<PropertyType>(Arrays.asList(attributes, contentVersion, descriptions, refsets, ids));
			eConceptUtil_.loadMetaDataItems(allPropertyTypes, metaDataRoot.getPrimordialUuid(), dos);
			
			// this must be stored later....
			EConcept ndfAllConceptRefset = refsets.getConcept("All NDF Concepts");

			ConsoleUtil.println("Metadata Summary");
			for (String s : eConceptUtil_.getLoadStats().getSummary())
			{
				ConsoleUtil.println(s);
			}

			eConceptUtil_.clearLoadStats();

			File dbPath = null;
			File classFile = null;
			//First, unzip any zip files
			for (File f : inputFile.listFiles())
			{
				if (f.getName().toLowerCase().endsWith(".zip"))
				{
					Unzip.unzip(f, inputFile);
				}
			}
			
			for (File f : inputFile.listFiles())
			{
				if (f.getName().toLowerCase().endsWith(".mdb") || f.getName().toLowerCase().endsWith(".accdb"))
				{
					if (dbPath != null)
					{
						throw new RuntimeException("More than one database (.mdb or .accdb) file found in input Path.  Can't handle.");
					}
					else
					{
						dbPath = f;
					}
				}
				else if (f.getName().toLowerCase().endsWith(".xls"))
				{
					if (classFile != null)
					{
						throw new RuntimeException("More than one .xls file found in input Path.  Can't handle.");
					}
					else
					{
						classFile = f;
					}
				}
			}

			if (classFile == null)
			{
				throw new RuntimeException("Could not find the VA Drug Class File");
			}

			if (dbPath == null)
			{
				throw new RuntimeException("Could not find an Access Database (*.mdb) in the input folder: " + inputFile.getAbsolutePath());
			}

			TreeMap<String, String> classCategoryMap = new TreeMap<String, String>(new AlphanumComparator(true));
			//For whatever reason, between versions 0 and 1, they changed what data they put in the VA_CLASS column in the database - switching from 
			//VA_CLASS to VA_Category - so we need a reverse map.
			//They also put everything into the DB as uppercase, while the spreadsheet sometimes has mixed case, so have to normalize the case too.
			HashMap<String, String> categoryClassMap = new HashMap<String, String>();

			// Read in the Drug Classes from Excel
			{
				HSSFWorkbook wb = new HSSFWorkbook(new FileInputStream(classFile));
				Sheet sheet = wb.getSheetAt(wb.getActiveSheetIndex());

				Iterator<Row> iter = sheet.rowIterator();
				while (iter.hasNext())
				{
					Row r = iter.next();
					String classID = r.getCell(0).getStringCellValue();
					if ("VA Class".equals(classID))  //header row
					{
						continue;
					}
					classCategoryMap.put(classID, r.getCell(1).getStringCellValue());
					categoryClassMap.put(r.getCell(1).getStringCellValue().toUpperCase(), classID);
				}

				wb = null;
			}

			ConsoleUtil.println("Read " + classCategoryMap.size() + " rows from the VA Drug Class file");

			Database db = DatabaseBuilder.open(dbPath);

			if (db.getTableNames().size() != 1)
			{
				throw new RuntimeException("Only expected to find one table within the Database.  Can't handle");
			}

			String tableName = db.getTableNames().iterator().next();

			Table table = db.getTable(tableName);

			HashMap<String, Property> propertyMap = new HashMap<String, Property>();

			for (Column c : table.getColumns())
			{
				String name = c.getName();
				Property p = null;
				for (PropertyType pt : allPropertyTypes)
				{
					p = pt.getProperty(name);
					if (p != null)
					{
						propertyMap.put(name, p);
						break;
					}
				}
				if (p == null)
				{
					throw new RuntimeException("Unhandled Column Name: " + name);
				}
			}

			EConcept ndfRoot = eConceptUtil_.createConcept("NDF");
			eConceptUtil_.addDescription(ndfRoot, "NDF", DescriptionType.SYNONYM, true, null, null, false);
			eConceptUtil_.addDescription(ndfRoot, "National Drug File", DescriptionType.SYNONYM, false, null, null, false);
			ConsoleUtil.println("Root concept FSN is 'NDF' and the UUID is " + ndfRoot.getPrimordialUuid());
			eConceptUtil_.addStringAnnotation(ndfRoot, releaseVersion, contentVersion.RELEASE.getUUID(), false);
			eConceptUtil_.addStringAnnotation(ndfRoot, loaderVersion, contentVersion.LOADER_VERSION.getUUID(), false);
			eConceptUtil_.addStringAnnotation(ndfRoot, tableName, ContentVersion.TABLE_NAME.getProperty().getUUID(), false);

			ndfRoot.writeExternal(dos);

			HashMap<String, UUID> classToHierarchy = new HashMap<String, UUID>();

			// We create a hierarchy that goes from Class -> Generic -> VA_Product -> (rows from the DB)

			// Create the basic Class hierarchy
			String lastLeadingChars = "";
			String leadingChars = "";
			UUID parentConcept = ndfRoot.getPrimordialUuid();

			for (Entry<String, String> entry : classCategoryMap.entrySet())
			{
				lastLeadingChars = leadingChars;
				leadingChars = entry.getKey().substring(0, 2);

				EConcept concept = eConceptUtil_.createConcept(ConverterUUID.createNamespaceUUIDFromString(entry.getKey()));
				eConceptUtil_.addDescriptions(concept, Arrays.asList(new ValuePropertyPair(entry.getValue(), descriptions.getProperty("VA Category"))));
				
				// Treat this as an ID here, rather than a refset.  Should really have VA_CLASS as an ID here, instead of refset, but punt for now.
				eConceptUtil_.addAdditionalIds(concept, entry.getKey(), refsets.getProperty("VA_CLASS").getUUID(), false);

				// Also add it as a refset member of VA_CLASS
				eConceptUtil_.addUuidAnnotation(concept.getConceptAttributes(), null, refsets.getProperty("VA_CLASS").getUUID());

				if (!lastLeadingChars.equals(leadingChars))
				{
					// Back up to root
					eConceptUtil_.addRelationship(concept, ndfRoot.getPrimordialUuid());
					parentConcept = concept.getPrimordialUuid();
				}
				else
				{
					eConceptUtil_.addRelationship(concept, parentConcept);
				}

				classToHierarchy.put(entry.getKey(), concept.getPrimordialUuid());
				concept.writeExternal(dos);
			}

			// Now, create the Generic hierarchy under the Class hierarchy
			// Iterate the DB, find all unique VA_CLASS / GENERIC / VA_Product triples
			HashMap<String, HashMap<String, HashSet<String>>> classGenericProductData = new HashMap<>();
			{
				Iterator<com.healthmarketscience.jackcess.Row> iter = table.iterator();
				while (iter.hasNext())
				{
					com.healthmarketscience.jackcess.Row item = iter.next();
					String vaClass = item.get("VA_CLASS").toString();
					String generic = item.get("GENERIC").toString();
					String vaProduct = item.get("VA_PRODUCT").toString();
					HashMap<String, HashSet<String>> genericValues = classGenericProductData.get(vaClass);
					if (genericValues == null)
					{
						genericValues = new HashMap<String, HashSet<String>>();
						classGenericProductData.put(vaClass, genericValues);
					}
					
					HashSet<String> vaProducts = genericValues.get(generic);
					if (vaProducts == null)
					{
						vaProducts = new HashSet<String>();
						genericValues.put(generic, vaProducts);
					}
					vaProducts.add(vaProduct);
				}
			}

			// Now, make a new map which gets us from the class / generic / product triple to the proper UUID for the generic.
			// generic UUIDs need to be generated with the class as part of the ID, as generic occurs under multiple Class values
			// (but is not the same concept for the purpose of this hierarchy)
			// and likewise, the vaProduct UUID needs to include both the class and generic values so that the hierarchy gets 
			// constructed properly.
			HashMap<String, UUID> class_generic_productToUUID = new HashMap<String, UUID>();

			for (Entry<String, HashMap<String, HashSet<String>>> classItem : classGenericProductData.entrySet())
			{
				UUID classUUID = classToHierarchy.get(classItem.getKey());
				if (classUUID == null)
				{
					//Possibly the reverse case found in version one - try the lookup through our reverse map.
					classUUID = classToHierarchy.get(categoryClassMap.get(classItem.getKey().toUpperCase()));
				}
				if (classUUID == null)
				{
					throw new RuntimeException("Null classUUID on " + classItem.getKey()+ ".  'Vomitrocious Data'");
				}

				for (Entry<String, HashSet<String>> genericItem : classItem.getValue().entrySet())
				{
					EConcept genericConcept = eConceptUtil_.createConcept(
							ConverterUUID.createNamespaceUUIDFromString(classItem.getKey() + ":" + genericItem.getKey()));
					eConceptUtil_.addDescriptions(genericConcept, Arrays.asList(new ValuePropertyPair(genericItem.getKey(), descriptions.getProperty("GENERIC"))));
					
					eConceptUtil_.addRelationship(genericConcept, classUUID);
					genericConcept.writeExternal(dos);
					for (String product : genericItem.getValue())
					{
						EConcept productConcept = eConceptUtil_.createConcept(
								ConverterUUID.createNamespaceUUIDFromString(classItem.getKey() + ":" + genericItem.getKey() + ":" + product));
						eConceptUtil_.addDescriptions(productConcept, Arrays.asList(new ValuePropertyPair(product, descriptions.getProperty("VA_PRODUCT"))));
						eConceptUtil_.addRelationship(productConcept, genericConcept.getPrimordialUuid());
						productConcept.writeExternal(dos);
						class_generic_productToUUID.put(classItem.getKey() + ":" + genericItem.getKey() + ":" + product, productConcept.getPrimordialUuid());
					}
				}
			}

			// don't need these anymore
			classGenericProductData = null;

			// Finally ready to process all of the concepts in the DB.
			HashSet<UUID> generatedUUIDs = new HashSet<UUID>();
			ArrayList<String> duplicates = new ArrayList<>();
			
			HashSet<String> uniqueFeederVerify = new HashSet<>();
			HashSet<String> uniqueNdfNdcVerify = new HashSet<>();

			Iterator<com.healthmarketscience.jackcess.Row> iter = table.iterator();
			while (iter.hasNext())
			{
				com.healthmarketscience.jackcess.Row row = iter.next();

				// So, it turns out, there is _nothing_ in the NDF database that is unique in version 1
				// 000378269510 AMITRIPTYLINE HCL 150MG TAB has two rows in the DB that are EXACT duplicates.

				String key = keyForRow(row);
				
				UUID conceptID = ConverterUUID.createNamespaceUUIDFromString(key);
				ConverterUUID.removeMapping(conceptID);  //Don't keep this mapping - I'd end up storing the entire DB because the string is so large.
				if (generatedUUIDs.contains(conceptID))
				{
					duplicates.add(key);
					continue;
				}
				else
				{
					generatedUUIDs.add(conceptID);
				}
				
				if (version >= 2)
				{
					//Use the NDF_NDC to generate UUIDs for the concept, since they are unique now.
					String feeder = asString(row.get("FEEDER"));
					String ndfNdc = asString(row.get("NDF_NDC"));
					if (uniqueFeederVerify.contains(feeder))
					{
						ConsoleUtil.printErrorln("Non-unique Feeder " + feeder);
					}
					else
					{
						uniqueFeederVerify.add(feeder);
					}
					
					if (uniqueNdfNdcVerify.contains(ndfNdc))
					{
						//fatal, since we are using this for conceptUUIDs
						throw new RuntimeException("Non-unique NDF_NDC " + ndfNdc);
					}
					else
					{
						uniqueNdfNdcVerify.add(ndfNdc);
					}
					//Can use the ConverterUUID class here, since we are just feeding in the ndfNDC, it won't be as overwhelming.
					conceptID = ConverterUUID.createNamespaceUUIDFromString(ndfNdc);
				}

				EConcept concept = eConceptUtil_.createConcept(conceptID);
				concept.setAnnotationIndexStyleRefex(true);
				String className = asString(row.get("VA_CLASS"));
				String generic = asString(row.get("GENERIC"));
				String product = asString(row.get("VA_PRODUCT"));
				UUID parent = class_generic_productToUUID.get(className + ":" + generic + ":" + product);
				if (parent == null)
				{
					ConsoleUtil.printErrorln("Can't find a parent for: " + key);
				}
				else
				{
					eConceptUtil_.addRelationship(concept, parent);
				}

				ArrayList<ValuePropertyPair> descriptionsToLoad = new ArrayList<>();
				boolean retired = false;
				for (String type : row.keySet())
				{
					Property p = propertyMap.get(type);
					String value = asString(row.get(type));
					if (value == null || value.length() == 0)
					{
						continue;
					}
					if (p == null)
					{
						ConsoleUtil.printErrorln("Couldn't find a property for " + type);
					}
					else if (p.getPropertyType() instanceof PT_Attributes)
					{
						if (type.equals("I_DATE_VAP"))
						{
							concept.getConceptAttributes().setStatusUuid(eConceptUtil_.statusRetiredUuid_);
							retired = true;
						}
						eConceptUtil_.addStringAnnotation(concept, value, p.getUUID(), false);
					}
					else if (p.getPropertyType() instanceof PT_Descriptions)
					{
						//batched in later
						descriptionsToLoad.add(new ValuePropertyPair(value, p));
					}
					else if (p.getPropertyType() instanceof PT_IDs)
					{
						eConceptUtil_.addAdditionalIds(concept, value, p.getUUID(), false);
					}
					else if (p.getPropertyType() instanceof PT_RefSets)
					{
						if (type.equals("VA_CLASS"))
						{
							// Link to the class concept we created - up two levels in the hierarchy
							UUID classUUID = classToHierarchy.get(value);
							if (classUUID == null)
							{
								//Possibly the reverse case found in version one - try the lookup through our reverse map.
								classUUID = classToHierarchy.get(categoryClassMap.get(value.toUpperCase()));
							}
							if (classUUID == null)
							{
								ConsoleUtil.printErrorln("Oops - null class");
								continue;
							}
							//Note, the WB 'indexing' function for attributes like this only seems to work for string attributes, 
							//doesn't work for UUID attributes.  Currently, you cannot list all the members of VA_CLASS, which is kind of silly.
							//Might have to make this back into a string attribute, or a member refset on the VA_CLASS concept, if anyone notices...
							eConceptUtil_.addUuidAnnotation(concept.getConceptAttributes(), classUUID, refsets.getProperty("VA_CLASS").getUUID());
						}
						else
						{
							ConsoleUtil.printErrorln("Unhandled refset type");
						}
					}
					else
					{
						ConsoleUtil.printErrorln("Unhandled Property Type: " + p.getPropertyType());
					}
				}
				ConsoleUtil.showProgress();

				eConceptUtil_.addDescriptions(concept, descriptionsToLoad);
				
				concept.writeExternal(dos);
				eConceptUtil_.addRefsetMember(ndfAllConceptRefset, concept.getPrimordialUuid(), null, !retired, null);
			}

			if (duplicates.size() > 0)
			{
				FileWriter fw = new FileWriter(new File(outputDirectory, "duplicates.txt"));
				fw.write(duplicatesHeader(table.iterator().next().keySet()));

				ConsoleUtil.printErrorln("The database contains " + duplicates.size()
						+ " duplicate rows.  They were not loaded into the WB DB.  Logged to 'duplicates.txt'");
				for (String s : duplicates)
				{
					fw.write(s);
					fw.write(System.getProperty("line.separator"));
				}
				fw.close();
			}

			eConceptUtil_.storeRefsetConcepts(refsets, dos);

			dos.flush();
			dos.close();

			for (String line : eConceptUtil_.getLoadStats().getSummary())
			{
				ConsoleUtil.println(line);
			}

			// this could be removed from final release. Just added to help debug editor problems.
			ConsoleUtil.println("Dumping UUID Debug File");
			ConverterUUID.dump(new File(outputDirectory, "ndfUuidDebugMap.txt"));
			ConsoleUtil.writeOutputToFile(new File(outputDirectory, "ConsoleOutput.txt").toPath());
		}
		catch (Exception ex)
		{
			throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
		}
	}

	private String asString(Object o)
	{
		return (o == null ? null : o.toString());
	}

	private String duplicatesHeader(Set<String> names)
	{
		StringBuilder sb = new StringBuilder();
		for (String s : names)
		{
			sb.append(s);
			sb.append("\t");
		}
		return sb.substring(0, sb.length() - 1);
	}

	private String keyForRow(com.healthmarketscience.jackcess.Row row)
	{
		StringBuilder sb = new StringBuilder();
		for (Object o : row.values())
		{
			if (o != null)
			{
				sb.append(asString(o));
			}
			sb.append("\t");
		}
		return sb.substring(0, sb.length() - 1);
	}

	public static void main(String[] args) throws MojoExecutionException
	{
		NDFImportMojo i = new NDFImportMojo();
		i.releaseVersion = "2013-02-blah blah";
		i.outputDirectory = new File("../ndf-econcept/target");
		i.inputFile = new File("../ndf-econcept/target/generated-resources/data/");
		i.execute();
	}
}