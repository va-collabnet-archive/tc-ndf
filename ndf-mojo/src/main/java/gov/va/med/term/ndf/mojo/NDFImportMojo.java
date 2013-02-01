package gov.va.med.term.ndf.mojo;

import gov.va.med.term.ndf.propertyTypes.PT_Attributes;
import gov.va.med.term.ndf.propertyTypes.PT_ContentVersion;
import gov.va.med.term.ndf.propertyTypes.PT_RefSets;
import gov.va.med.term.ndf.propertyTypes.PT_ContentVersion.ContentVersion;
import gov.va.med.term.ndf.propertyTypes.PT_Descriptions;
import gov.va.med.term.ndf.util.AlphanumComparator;
import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import gov.va.oia.terminology.converters.sharedUtils.EConceptUtility;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_ContentVersion.BaseContentVersion;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.Property;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.PropertyType;
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.dwfa.ace.refset.ConceptConstants;
import org.dwfa.cement.ArchitectonicAuxiliary;
import org.ihtsdo.etypes.EConcept;
import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database;
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
    private String uuidRoot_ = "gov.va.med.term.ndf:";
    
	private PropertyType attributes = new PT_Attributes(uuidRoot_);
	private PropertyType contentVersion = new PT_ContentVersion(uuidRoot_);
	private PropertyType descriptions = new PT_Descriptions(uuidRoot_);
	private PropertyType refsets = new PT_RefSets(uuidRoot_);
	
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
    private String srcContentVersion;

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
			
			eConceptUtil_ = new EConceptUtility(uuidRoot_);

			EConcept metaDataRoot = eConceptUtil_.createConcept("NDF Metadata", ArchitectonicAuxiliary.Concept.ARCHITECTONIC_ROOT_CONCEPT.getPrimoridalUid());
			metaDataRoot.writeExternal(dos);
			
			List<PropertyType> allPropertyTypes = new ArrayList<PropertyType>(Arrays.asList(attributes, contentVersion, descriptions));
			eConceptUtil_.loadMetaDataItems(allPropertyTypes, metaDataRoot.getPrimordialUuid(), dos);
			
			//This is chosen to line up with other va refsets
            EConcept vaRefsets = eConceptUtil_.createAndStoreMetaDataConcept(ConverterUUID.nameUUIDFromBytes(("gov.va.refset.VA Refsets").getBytes()), 
                    "VA Refsets", ConceptConstants.REFSET.getUuids()[0], dos);
            
            EConcept ndfRefsets = eConceptUtil_.createAndStoreMetaDataConcept("NDF Refsets", vaRefsets.getPrimordialUuid(), dos);
			
			//handle refsets myself - they get two parents.
			EConcept refsetMetaData = eConceptUtil_.createAndStoreMetaDataConcept(refsets.getPropertyTypeUUID(), refsets.getPropertyTypeDescription(), metaDataRoot.getPrimordialUuid(), dos);
			for (Property p : refsets.getProperties())
			{
			    String name = null;
			    String synonym = null;
			    if (p.getUseSrcDescriptionForFSN() && p.getSourcePropertyDescription() != null)
			    {
			        name = p.getSourcePropertyDescription();
			        synonym = p.getSourcePropertyName();
			    }
			    else
			    {
			        name = p.getSourcePropertyName();
			        synonym = p.getSourcePropertyDescription();
			    }
			    EConcept refSet = eConceptUtil_.createConcept(p.getUUID(),name , null);
			    if (synonym != null)
			    {
			        eConceptUtil_.addDescription(refSet, synonym, eConceptUtil_.synonymAcceptableUuid_, false);
			    }
			    eConceptUtil_.addRelationship(refSet, ndfRefsets.getPrimordialUuid());
			    eConceptUtil_.addRelationship(refSet, refsetMetaData.getPrimordialUuid());
			    refSet.writeExternal(dos);
			}
			
			
			allPropertyTypes.add(refsets);
			
			ConsoleUtil.println("Metadata Summary");
			for (String s : eConceptUtil_.getLoadStats().getSummary())
			{
			    ConsoleUtil.println(s);
			}
			
			eConceptUtil_.clearLoadStats();
			
			File dbPath = null;
			File classFile = null;
			for (File f : inputFile.listFiles())
			{
			    if (f.getName().toLowerCase().endsWith(".mdb"))
			    {
			        if (dbPath != null)
			        {
			            throw new RuntimeException("More than one .mdb file found in input Path.  Can't handle.");
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
			
            // Read in the Drug Classes from Excel
			{
    			HSSFWorkbook wb = new HSSFWorkbook(new FileInputStream(classFile));
    			Sheet sheet = wb.getSheetAt(wb.getActiveSheetIndex());
    
    			Iterator<Row> iter = sheet.rowIterator();
    			while (iter.hasNext())
    			{
    			    Row r = iter.next();
    			    String classID = r.getCell(0).getStringCellValue();
    			    if ("VA Class".equals(classID))
    			    {
    			        continue;
    			    }
    			    classCategoryMap.put(classID, r.getCell(1).getStringCellValue());
    			}
    			
    			wb = null;
			}
			
			ConsoleUtil.println("Read " + classCategoryMap.size() + " rows from the VA Drug Class file");
			
			Database db = Database.open(dbPath, true);
			
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
			            propertyMap.put(name,  p);
			            break;
			        }
			    }
			    if (p == null)
			    {
			        throw new RuntimeException("Unhandled Column Name: " + name);
			    }
			}
			
			EConcept ndfRoot = eConceptUtil_.createConcept("NDF");
			eConceptUtil_.addStringAnnotation(ndfRoot, srcContentVersion, BaseContentVersion.RELEASE.getProperty().getUUID(), false);
			eConceptUtil_.addStringAnnotation(ndfRoot, loaderVersion, BaseContentVersion.LOADER_VERSION.getProperty().getUUID(), false);
			eConceptUtil_.addStringAnnotation(ndfRoot, tableName, ContentVersion.TABLE_NAME.getProperty().getUUID(), false);
						
			ndfRoot.writeExternal(dos);
			
			HashMap<String, UUID> classToHierarchy = new HashMap<String, UUID>();
			
			//Create the basic Class hierarchy
			String lastLeadingChars = "";
            String leadingChars = "";
            UUID parentConcept = ndfRoot.getPrimordialUuid();
            
            for (Entry<String, String> entry : classCategoryMap.entrySet())
            {
                lastLeadingChars = leadingChars;
                leadingChars = entry.getKey().substring(0, 2);
                
                EConcept concept = eConceptUtil_.createConcept(entry.getKey(), entry.getValue());
                //Treat this as an ID here, rather than a refset
                eConceptUtil_.addAdditionalIds(concept, entry.getKey(), refsets.getProperty("VA_CLASS").getUUID(), false);
                
                //Also add it as a refset
                eConceptUtil_.addUuidAnnotation(concept.getConceptAttributes(), null, refsets.getProperty("VA_CLASS").getUUID());
                
                if (!lastLeadingChars.equals(leadingChars))
                {
                    //Back up to root
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
            
            

            //Now, create the Generic hierarchy under the Class hierarchy
            //Iterate the DB, find all unique VA_CLASS / GENERIC pairs
            HashMap<String, HashSet<String>> classGenericData = new HashMap<String, HashSet<String>>();
            {
                Iterator<Map<String, Object>> iter = table.iterator();
                while (iter.hasNext())
                {
                    Map<String, Object> item = iter.next();
                    String vaClass = item.get("VA_CLASS").toString();
                    String generic = item.get("GENERIC").toString();
                    HashSet<String> values = classGenericData.get(vaClass);
                    if (values == null)
                    {
                        values = new HashSet<String>();
                        classGenericData.put(vaClass, values);
                    }
                    values.add(generic);
                }
            }
            
            //Now, make a new map which gets us from the class / generic pair to the proper UUID for the generic.
            //generic UUIDs need to be generated with the class as part of the ID, as generic occurs under multiple Class values
            //(but is not the same concept for the purpose of this hierarchy)
            HashMap<String, UUID> class_genericToUUID = new HashMap<String, UUID>();
            
            for (Entry<String, HashSet<String>> item : classGenericData.entrySet())
            {
                UUID classUUID = classToHierarchy.get(item.getKey());

                for (String generic : item.getValue())
                {
                    EConcept concept = eConceptUtil_.createConcept(item.getKey() + ":" + generic, generic);
                    eConceptUtil_.addDescription(concept, generic, descriptions.getProperty("GENERIC").getUUID(), false);
                    eConceptUtil_.addRelationship(concept, classUUID);
                    class_genericToUUID.put(item.getKey() + ":" + generic, concept.getPrimordialUuid());
                    concept.writeExternal(dos);
                }
            }
            
            
            //don't need these anymore
            classGenericData = null;
            
            //Finally ready to process all of the concepts in the DB.
            HashSet<UUID> generatedUUIDs = new HashSet<UUID>();
            ArrayList<String> duplicates = new ArrayList<>();
            
            Iterator<Map<String, Object>> iter = table.iterator();
            while (iter.hasNext())
            {
                Map<String, Object> row = iter.next();
                
                //So, it turns out, there is _nothing_ in the NDF database that is unique.
                //000378269510 AMITRIPTYLINE HCL 150MG TAB has two rows in the DB that are EXACT duplicates.

                String key = keyForRow(row);
                //Purposefully not using the ConvertUUID class - don't want to stick this entire value into the map. 
                //I'd end up rewriting the entire DB, sine there isn't any unique column in this DB.
                
                UUID conceptId = UUID.nameUUIDFromBytes((uuidRoot_ + key).getBytes());
                if (generatedUUIDs.contains(conceptId))
                {
                    duplicates.add(key);
                    continue;
                }
                else
                {
                    generatedUUIDs.add(conceptId);
                }
                
                String fsn = asString(row.get("VA_PRODUCT"));

                EConcept concept = eConceptUtil_.createConcept(conceptId, fsn, null);
                String className = asString(row.get("VA_CLASS"));
                String generic = asString(row.get("GENERIC"));
                UUID parent = class_genericToUUID.get(className + ":" + generic);
                if (parent == null)
                {
                    ConsoleUtil.printErrorln("Can't find a parent for: " + key);
                }
                else
                {
                    eConceptUtil_.addRelationship(concept, parent);
                }
                
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
                        }
                             eConceptUtil_.addStringAnnotation(concept, value, p.getUUID(), false);
                     }
                    else if (p.getPropertyType() instanceof PT_RefSets)
                    {
                        if (type.equals("VA_CLASS"))
                        {
                            //Link to the class concept we created 
                            UUID classUUID = classToHierarchy.get(value);
                            if (classUUID == null)
                            {
                                ConsoleUtil.printErrorln("Oops - null class");
                                continue;
                            }
                            eConceptUtil_.addUuidAnnotation(concept.getConceptAttributes(), refsets.getProperty("VA_CLASS").getUUID(), classUUID);
                        }
                    }
                    else if (p.getPropertyType() instanceof PT_Descriptions)
                    {
                        eConceptUtil_.addDescription(concept, value, p.getUUID(), false);
                    }
                    else
                    {
                        ConsoleUtil.printErrorln("Unhandled Property Type: " + p.getPropertyType());
                    }
                }
                ConsoleUtil.showProgress();

                concept.writeExternal(dos);
            }
            
            if (duplicates.size() > 0)
            {
                FileWriter fw = new FileWriter(new File(outputDirectory, "duplicates.txt"));
                fw.write(duplicatesHeader(table.iterator().next().keySet()));
                
                ConsoleUtil.printErrorln("The database contains " + duplicates.size() + " duplicate rows.  They were not loaded into the WB DB.  Logged to 'duplicates.txt'");
                for (String s : duplicates)
                {
                    fw.write(s);
                    fw.write(System.getProperty("line.separator"));
                }
                fw.close();
            }

			dos.flush();
			dos.close();

			for (String line : eConceptUtil_.getLoadStats().getSummary())
			{
				ConsoleUtil.println(line);
			}

			// this could be removed from final release. Just added to help debug editor problems.
			ConsoleUtil.println("Dumping UUID Debug File");
			ConverterUUID.dump(new File(outputDirectory, "chdrUuidDebugMap.txt"));
			
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
	
	private String keyForRow(Map<String, Object> row)
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
        i.outputDirectory = new File("../ndf-econcept/target");
        i.inputFile = new File("/mnt/d/Work/Apelon/Workspaces/Loaders/ndf/ndf-econcept/NDF Data/");
        i.execute();
    }
}