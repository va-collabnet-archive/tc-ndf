package gov.va.med.term.ndf.mojo;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelDataReader implements DataReader
{
	private XSSFWorkbook wb_;
	Sheet sheet_;

	public ExcelDataReader(File dbPath) throws IOException
	{
		ConsoleUtil.println("Opening " + dbPath + " as an Excel File");
		wb_ = new XSSFWorkbook(new FileInputStream(dbPath));
		if (wb_.getNumberOfSheets() > 1)
		{
			System.err.println("Warning - Only expected to find one sheet - using the active sheet");
		}
		sheet_ = wb_.getSheetAt(wb_.getActiveSheetIndex());
	}

	@Override
	public ArrayList<String> getColumnNames() throws IOException
	{
		ArrayList<String> result = new ArrayList<>();
		Row r = sheet_.getRow(0);
		Iterator<Cell> cells = r.cellIterator();
		while (cells.hasNext())
		{
			Cell c = cells.next();

			switch (c.getCellType())
			{
				case Cell.CELL_TYPE_BLANK:
					result.add("");
					break;
				case Cell.CELL_TYPE_STRING:
					result.add(c.getStringCellValue());
					break;

				default:
					throw new RuntimeException("Unhandeled cell type");
			}
		}
		return result;
	}

	@Override
	public String getTableName() throws IOException
	{
		return sheet_.getSheetName();
	}

	@Override
	public Iterator<Map<String, Object>> getRows() throws IOException
	{
		return new Iterator<Map<String, Object>>()
		{
			int row = 1;  // row 0 is the header
			ArrayList<String> colNames = getColumnNames();

			@Override
			public boolean hasNext()
			{
				Row r = sheet_.getRow(row);
				if (r == null)
				{
					return false;
				}
				Cell cell = r.getCell(0);
				if (cell == null || cell.getCellType() == Cell.CELL_TYPE_BLANK || 
						(cell.getCellType() == Cell.CELL_TYPE_STRING && cell.getStringCellValue().length() == 0))
				{
					return false;
				}
				return true;
			}

			@Override
			public Map<String, Object> next()
			{
				HashMap<String, Object> rowData = new HashMap<>();

				for (int i = 0; i < colNames.size(); i++)
				{
					Cell cell = sheet_.getRow(row).getCell(i);
					if (cell == null)
					{
						rowData.put(colNames.get(i), "");
						continue;
					}
					switch (cell.getCellType())
					{
						case Cell.CELL_TYPE_BLANK:
							rowData.put(colNames.get(i), "");
							break;
						case Cell.CELL_TYPE_STRING:
							rowData.put(colNames.get(i), cell.getStringCellValue());
							break;
						case Cell.CELL_TYPE_NUMERIC:
							rowData.put(colNames.get(i), cell.getNumericCellValue());
							break;

						default:
							throw new RuntimeException("Unhandeled cell type");
					}
				}
				row++;
				return rowData;
			}

			@Override
			public void remove()
			{
				throw new RuntimeException("unimplemented");
			}
		};
	}
}
