package gov.va.med.term.ndf.mojo;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

public class AccessDataReader implements DataReader
{
	private Database db_;
	private String tableName_;

	public AccessDataReader(File dbPath) throws IOException
	{
		ConsoleUtil.println("Opening " + dbPath + " as an Access Database");
		db_ = DatabaseBuilder.open(dbPath);
		if (db_.getTableNames().size() != 1)
		{
			throw new RuntimeException("Only expected to find one table within the Database.  Can't handle");
		}
		else
		{
			tableName_ = db_.getTableNames().iterator().next();
		}
	}

	@Override
	public String getTableName() throws IOException
	{
		return tableName_;
	}

	@Override
	public ArrayList<String> getColumnNames() throws IOException
	{
		Table table = db_.getTable(tableName_);

		ArrayList<String> result = new ArrayList<>();
		for (Column c : table.getColumns())
		{
			result.add(c.getName());
		}
		return result;
	}

	@Override
	public Iterator<Map<String, Object>> getRows() throws IOException
	{
		Table table = db_.getTable(tableName_);
		return new IteratorWrapper(table.iterator());
	}

	private class IteratorWrapper implements Iterator<Map<String, Object>>
	{
		private Iterator<Row> sourceData_;

		private IteratorWrapper(Iterator<Row> rowIterator)
		{
			sourceData_ = rowIterator;
		}

		@Override
		public boolean hasNext()
		{
			return sourceData_.hasNext();
		}

		@Override
		public Map<String, Object> next()
		{
			return sourceData_.next();
		}

		@Override
		public void remove()
		{
			sourceData_.remove();
		}
	}
}
