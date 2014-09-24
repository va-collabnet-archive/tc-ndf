package gov.va.med.term.ndf.mojo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public interface DataReader
{
	public ArrayList<String> getColumnNames() throws IOException;
	
	public String getTableName() throws IOException;
	
	public Iterator<Map<String,Object>> getRows() throws IOException;
}
