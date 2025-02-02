package de.larssh.budget.aggregator.utils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import org.apache.poi.ss.formula.eval.ErrorEval;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CellValues {
	public static final CellValue _NONE = create(CellType._NONE, 0, false, null, 0);

	public static final CellValue BLANK = create(CellType.BLANK, 0, false, null, 0);

	private static final String DATE_STRING_VALUE = "__DATE__";

	private static final Map<Workbook, FormulaEvaluator> FORMULA_EVALUATORS = new WeakHashMap<>();

	@SuppressWarnings({ "java:S112", "java:S3011" })
	private CellValue create(final CellType cellType,
			final double numberValue,
			final boolean booleanValue,
			@Nullable final String textValue,
			final int errorCode) {
		try {
			final Constructor<CellValue> constructor = CellValue.class
					.getDeclaredConstructor(CellType.class, double.class, boolean.class, String.class, int.class);
			constructor.setAccessible(true);
			return constructor.newInstance(cellType, numberValue, booleanValue, textValue, errorCode);
		} catch (final ReflectiveOperationException e) {
			throw new RuntimeException("Failed creating CellValue instance", e);
		}
	}

	@SuppressWarnings("resource")
	public static CellValue create(@Nullable final Cell cell, final boolean evaluateFormula) {
		if (cell == null) {
			return new CellValue("");
		}
		switch (cell.getCellType()) {
		case BOOLEAN:
			return cell.getBooleanCellValue() ? CellValue.TRUE : CellValue.FALSE;
		case BLANK:
			return new CellValue("");
		case ERROR:
			return CellValue.getError(cell.getErrorCellValue());
		case FORMULA:
			return evaluateFormula
					? evaluateFormula(cell)
					: create(CellType.FORMULA, 0, false, cell.getCellFormula(), 0);
		case NUMERIC:
			return DateUtil.isCellDateFormatted(cell)
					? create(CellType.NUMERIC,
							cell.getNumericCellValue(),
							Workbooks.isUsing1904DateWindowing(cell.getSheet().getWorkbook()),
							DATE_STRING_VALUE,
							0)
					: new CellValue(cell.getNumericCellValue());
		case STRING:
			return new CellValue(cell.getStringCellValue());
		case _NONE:
		default:
			return CellValue.getError(FormulaError.FUNCTION_NOT_IMPLEMENTED.getLongCode());
		}
	}

	@SuppressWarnings("resource")
	private static CellValue evaluateFormula(final Cell cell) {
		return FORMULA_EVALUATORS
				.computeIfAbsent(cell.getSheet().getWorkbook(),
						workbook -> workbook.getCreationHelper().createFormulaEvaluator())
				.evaluate(cell);
	}

	public static Optional<LocalDateTime> getLocalDateTime(final CellValue value) {
		return isDate(value)
				? Optional.of(DateUtil.getLocalDateTime(value.getNumberValue(), value.getBooleanValue()))
				: Optional.empty();
	}

	public static String getAsString(final CellValue value) {
		switch (value.getCellType()) {
		case BOOLEAN:
			return value.getBooleanValue() ? "TRUE" : "FALSE";
		case BLANK:
			return "";
		case ERROR:
			return ErrorEval.getText(value.getErrorValue());
		case NUMERIC:
			return getLocalDateTime(value).map(localDateTime -> localDateTime.format(DateTimeFormatter.ISO_LOCAL_TIME))
					.orElseGet(() -> {
						final String numericValue = Double.toString(value.getNumberValue());
						return numericValue.endsWith(".0")
								? numericValue.substring(0, numericValue.length() - 2)
								: numericValue;
					});
		case STRING:
		case FORMULA:
			return value.getStringValue();
		case _NONE:
		default:
			return "Unexpected Cell Type: " + value.getCellType();
		}
	}

	public static boolean isDate(final CellValue value) {
		return value.getCellType() == CellType.NUMERIC && DATE_STRING_VALUE.equals(value.getStringValue());
	}
}
