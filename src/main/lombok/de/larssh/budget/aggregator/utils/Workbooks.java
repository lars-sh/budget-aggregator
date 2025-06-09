package de.larssh.budget.aggregator.utils;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Workbooks {
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
}
