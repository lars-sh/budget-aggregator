package de.larssh.budget.aggregator.utils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.larssh.utils.collection.Iterators;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Workbooks {
	public static Stream<Cell> cells(final Row row) {
		final AtomicInteger index = new AtomicInteger(row.getFirstCellNum());
		final int lastColumnIndex = row.getLastCellNum();

		return Iterators.stream(state -> {
			final int currentIndex = index.getAndIncrement();
			return currentIndex > lastColumnIndex ? state.endOfData() : row.getCell(currentIndex);
		});
	}

	@SuppressWarnings({ "checkstyle:SuppressWarnings", "PMD.SimplifyBooleanReturns", "resource" })
	@SuppressFBWarnings(value = "ITC_INHERITANCE_TYPE_CHECKING", justification = "helper method for external code")
	public static boolean isUsing1904DateWindowing(final Workbook workbook) {
		if (workbook instanceof HSSFWorkbook) {
			return ((HSSFWorkbook) workbook).getInternalWorkbook().isUsing1904DateWindowing();
		}
		if (workbook instanceof XSSFWorkbook) {
			return ((XSSFWorkbook) workbook).isDate1904();
		}
		if (workbook instanceof SXSSFWorkbook) {
			return ((SXSSFWorkbook) workbook).getXSSFWorkbook().isDate1904();
		}
		return false;
	}

	public static Stream<Row> rows(final Sheet sheet) {
		final AtomicInteger index = new AtomicInteger(sheet.getFirstRowNum());
		final int lastRowIndex = sheet.getLastRowNum();

		return Iterators.stream(state -> {
			final int currentIndex = index.getAndIncrement();
			return currentIndex > lastRowIndex ? state.endOfData() : sheet.getRow(currentIndex);
		});
	}

	public static Stream<Sheet> sheets(final Workbook workbook) {
		final AtomicInteger index = new AtomicInteger(0);
		final int numberOfSheets = workbook.getNumberOfSheets();

		return Iterators.stream(state -> {
			final int currentIndex = index.getAndIncrement();
			return currentIndex >= numberOfSheets ? state.endOfData() : workbook.getSheetAt(currentIndex);
		});
	}
}
