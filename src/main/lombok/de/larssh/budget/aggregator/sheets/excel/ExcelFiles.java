package de.larssh.budget.aggregator.sheets.excel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.poi.hssf.usermodel.HSSFWorkbookFactory;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.ss.util.SheetUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookFactory;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STTotalsRowFunction;

import de.larssh.budget.aggregator.data.Account;
import de.larssh.budget.aggregator.data.Balance;
import de.larssh.budget.aggregator.data.Budget;
import de.larssh.budget.aggregator.data.BudgetReference;
import de.larssh.budget.aggregator.data.BudgetType;
import de.larssh.budget.aggregator.data.Budgets;
import de.larssh.budget.aggregator.data.Product;
import de.larssh.budget.aggregator.sheets.csv.CsvFiles;
import de.larssh.utils.Finals;
import de.larssh.utils.Nullables;
import de.larssh.utils.OptionalInts;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.collection.Maps;
import de.larssh.utils.text.StringParseException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
@SuppressWarnings({ "PMD.CouplingBetweenObjects", "PMD.ExcessiveImports" })
public class ExcelFiles {
	/**
	 * Remark: {@link ExcelSheet#isApplyBudgetTypeSign()} requires this constant to
	 * differ from {@link CsvFiles#COLUMN_NAME_MUNICIPALITY}.
	 */
	@PackagePrivate
	static final String COLUMN_NAME_MUNICIPALITY = Finals.constant("Gemeinde");

	static {
		// Making sure that both Workbook Factories are registered to support XLS and
		// XLSX. Registering automatically might be a problem when creating a JAR with
		// all dependencies.
		WorkbookFactory.addProvider(new XSSFWorkbookFactory());
		WorkbookFactory.addProvider(new HSSFWorkbookFactory());
	}

	public static List<Budget> read(final Path source) throws IOException, StringParseException {
		try (InputStream inputStream = Files.newInputStream(source);
				Workbook workbook = WorkbookFactory.create(inputStream)) {
			final String fileName = Nullables.orElseThrow(source.getFileName()).toString();
			return Budgets.of(new ExcelSheets(fileName, workbook));
		}
	}

	public static void write(final List<Budget> budgets, final OutputStream outputStream) throws IOException {
		new ExcelFileWriter(budgets, outputStream).write();
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static class ExcelFileWriter {
		/**
		 * Width of the auto filter control in Excel, calculated using the difference of
		 * a cell without and with auto filter
		 */
		private static final int AUTO_FILTER_WIDTH = 563;

		private static final Map<BudgetType, SimplifiedCellStyle> SIMPLIFIED_CELL_STYLES
				= Maps.<BudgetType, SimplifiedCellStyle>builder()
						.put(BudgetType.of("Plan"), SimplifiedCellStyle.NORMAL)
						.put(BudgetType.of("Ist"), SimplifiedCellStyle.BOLD)
						.unmodifiable();

		/**
		 * Width of one character
		 */
		private static final int CHARACTER_WIDTH = 256;

		/**
		 * The maximum width of a column
		 */
		private static final int COLUMN_MAX_WIDTH = 255 * CHARACTER_WIDTH;

		private static final String DATA_FORMAT_CURRENCY = "#,##0.00\\ \"€\";[Red]\\-#,##0.00\\ \"€\";\"\"";

		private static final String SHEET_NAME_ACCOUNTS = "Konten";

		private static final String SHEET_NAME_PRODUCTS = "Produkte";

		private static final String COLUMN_NAME_ACCOUNT = "Konto";

		private static final String COLUMN_NAME_ACCOUNT_DESCRIPTION = "Kontobeschreibung";

		private static final String COLUMN_NAME_DESCRIPTION = "Beschreibung";

		private static final String COLUMN_NAME_PRODUCT = "Produkt";

		private static final String COLUMN_NAME_PRODUCT_DESCRIPTION = "Produktbeschreibung";

		private static final String COLUMN_NAME_SUM = "Summieren";

		/**
		 * Excel data format for any number with any number of decimal places
		 */
		private static final ThreadLocal<NumberFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
			final DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
			format.setMaximumFractionDigits(Integer.MAX_VALUE);
			return format;
		});

		private static void addBudgetsComment(final Cell cell, final Budget budget) {
			final StringBuilder comment = new StringBuilder();

			for (final Entry<BudgetReference, String> entry : budget.getReferences().entrySet()) {
				if (comment.length() > 0) {
					comment.append('\n');
				}
				comment.append(entry.getKey().getDisplayValue()) //
						.append(": ")
						.append(entry.getValue());
			}

			if (comment.length() > 0) {
				addComment(cell, comment.toString());
			}
		}

		@SuppressWarnings("checkstyle:MagicNumber")
		private static void addComment(final Cell cell, final String value) {
			@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
			final CreationHelper creationHelper = cell.getSheet().getWorkbook().getCreationHelper();

			final ClientAnchor clientAnchor = creationHelper.createClientAnchor();
			clientAnchor.setCol1(cell.getColumnIndex());
			clientAnchor.setCol2(cell.getColumnIndex() + 3);
			clientAnchor.setRow1(cell.getRow().getRowNum());
			clientAnchor.setRow2(cell.getRow().getRowNum() + 5);

			final Comment comment = cell.getSheet().createDrawingPatriarch().createCellComment(clientAnchor);
			comment.setString(creationHelper.createRichTextString(value));

			cell.setCellComment(comment);
		}

		/**
		 * Appends a row to {@code sheet} below the currently last row.
		 *
		 * @param sheet the sheet to modify
		 * @return the created row
		 */
		private static Row appendRow(final Sheet sheet) {
			return CellUtil.getRow(sheet.getLastRowNum() + 1, sheet);
		}

		/**
		 * Creates a table with auto filter, default style and {@ode name}, spanning all
		 * curently available cells.
		 *
		 * @param sheet the sheet to modify
		 * @param name  the table name
		 * @return the created table
		 */
		private static XSSFTable createTable(final Sheet sheet, final String name) {
			final XSSFTable table = ((XSSFSheet) sheet).createTable(new AreaReference(new CellReference(0, 0),
					new CellReference(sheet.getLastRowNum(), CellUtil.getRow(0, sheet).getLastCellNum() - 1),
					SpreadsheetVersion.EXCEL2007));
			table.setName(name);
			table.setDisplayName(name);
			table.setStyleName("TableStyleLight1");
			table.getCTTable().addNewAutoFilter();
			table.getCTTable().getTableStyleInfo().setShowRowStripes(true);
			return table;
		}

		private static SimplifiedCellStyle getSimplifiedCellStyle(final Budget budget) {
			return Nullables.orElse(SIMPLIFIED_CELL_STYLES.get(budget.getType()), SimplifiedCellStyle.ITALIC);
		}

		/**
		 * Sets a numeric {@code value} for {@code cell}.
		 *
		 * <p>
		 * This method allows setting e.g. precise {@link java.math.BigDecimal} values,
		 * but does not permit infinite and {@code NaN} values.
		 *
		 * @param cell  the cell to modify
		 * @param value the value to set
		 */
		private static void setCellValue(final Cell cell, final Number value) {
			final CTCell ctCell = ((XSSFCell) cell).getCTCell();
			ctCell.setT(STCellType.N);
			ctCell.setV(DECIMAL_FORMAT.get().format(value));
		}

		List<Budget> budgets;

		OutputStream outputStream;

		Set<String> budgetColumnNamesUsed = new HashSet<>();

		Map<Budget, String> budgetColumnNames = new IdentityHashMap<>();

		/**
		 * Cache to simplify reusing {@link CellStyle} instances
		 */
		Map<String, CellStyle> cellStyleCache = new HashMap<>();

		@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
		private <T> Cell appendCell(final Row row,
				final SimplifiedCellStyle simplifiedCellStyle,
				final Optional<String> dataFormat,
				final BiConsumer<Cell, T> setValue,
				final Optional<T> value) {
			final Cell cell = CellUtil.getCell(row, Math.max(0, row.getLastCellNum()));
			getCellStyle(row.getSheet().getWorkbook(), simplifiedCellStyle, dataFormat).ifPresent(cell::setCellStyle);
			if (value.isPresent()) {
				setValue.accept(cell, value.get());
			}
			return cell;
		}

		private Cell appendBoolean(final Row row, final boolean value) {
			return appendCell(row,
					SimplifiedCellStyle.NORMAL,
					Optional.empty(),
					Cell::setCellValue,
					Optional.of(value));
		}

		private Iterator<CTTableColumn> appendTotalsRow(final Row row, final CTTable table, final int emptyColumns) {
			table.setTotalsRowCount(1);
			final Iterator<CTTableColumn> columns = table.getTableColumns().getTableColumnList().iterator();
			for (int i = 0; i < emptyColumns; i += 1) {
				columns.next().setTotalsRowLabel("");
				appendStrings(row, "");
			}
			return columns;
		}

		private Cell appendFormula(final Row row,
				final SimplifiedCellStyle simplifiedCellStyle,
				final String dataFormat,
				final String formula) {
			return appendCell(row,
					simplifiedCellStyle,
					Optional.of(dataFormat),
					Cell::setCellFormula,
					Optional.of(formula));
		}

		private Cell appendNumber(final Row row, final OptionalInt value) {
			return appendCell(row,
					SimplifiedCellStyle.NORMAL,
					Optional.empty(),
					ExcelFileWriter::setCellValue,
					OptionalInts.boxed(value));
		}

		private Cell appendNumber(final Row row,
				final SimplifiedCellStyle simplifiedCellStyle,
				final String dataFormat,
				final Optional<? extends Number> value) {
			return appendCell(row, simplifiedCellStyle, Optional.of(dataFormat), ExcelFileWriter::setCellValue, value);
		}

		private Cell appendString(final Row row, final String value) {
			return appendString(row, SimplifiedCellStyle.NORMAL, value);
		}

		private Cell appendString(final Row row, final SimplifiedCellStyle simplifiedCellStyle, final String value) {
			return appendCell(row, simplifiedCellStyle, Optional.empty(), Cell::setCellValue, Optional.of(value));
		}

		private void appendStrings(final Row row, final String... values) {
			for (final String value : values) {
				appendString(row, value);
			}
		}

		private String getBudgetColumnName(final Budget budget) {
			// 1. Try to find a cached name
			final String cachedName = budgetColumnNames.get(budget);
			if (cachedName != null) {
				return cachedName;
			}

			// 2. Create the straight-forward column name
			final String simpleName = String.format("%s %d", budget.getType().getName(), budget.getYear());
			String columnName = simpleName;

			// 3. Add a number in case of multiple columns with the same name
			for (int count = 2; budgetColumnNamesUsed.contains(columnName); count += 1) {
				columnName = String.format("%s (%d)", simpleName, count);
			}

			// 4. Add to cache and return
			budgetColumnNamesUsed.add(columnName);
			budgetColumnNames.put(budget, columnName);
			return columnName;
		}

		private Optional<CellStyle> getCellStyle(final Workbook workbook,
				final SimplifiedCellStyle simplifiedCellStyle,
				final Optional<String> dataFormat) {
			if (simplifiedCellStyle == SimplifiedCellStyle.NORMAL && !dataFormat.isPresent()) {
				return Optional.empty();
			}

			return Optional.of(
					cellStyleCache.computeIfAbsent(simplifiedCellStyle.name() + ';' + dataFormat.orElse(""), key -> {
						final CellStyle cellStyle = workbook.createCellStyle();
						dataFormat.ifPresent(
								format -> cellStyle.setDataFormat(workbook.createDataFormat().getFormat(format)));

						if (simplifiedCellStyle != SimplifiedCellStyle.NORMAL) {
							final Font font = workbook.createFont();
							if (simplifiedCellStyle == SimplifiedCellStyle.BOLD) {
								font.setBold(true);
							} else if (simplifiedCellStyle == SimplifiedCellStyle.ITALIC) {
								font.setItalic(true);
							}
							cellStyle.setFont(font);
						}
						return cellStyle;
					}));
		}

		@PackagePrivate
		void write() throws IOException {
			try (XSSFWorkbook workbook = new XSSFWorkbook()) {
				workbook.setCellFormulaValidation(false);

				writeProducts(workbook.createSheet(SHEET_NAME_PRODUCTS));
				writeAccounts(workbook.createSheet(SHEET_NAME_ACCOUNTS));

				// Auto Size Columns
				workbook.sheetIterator().forEachRemaining(sheet -> {
					final int numberOfColumns = sheet.getRow(0).getLastCellNum();
					for (int columnIndex = 0; columnIndex < numberOfColumns; columnIndex += 1) {
						sheet.autoSizeColumn(columnIndex);

						// Add the width of the auto filter
						final double widthOfHeader = SheetUtil.getColumnWidth(sheet, columnIndex, false, 0, 0);
						if (widthOfHeader != -1) {
							final int intWidth = (int) Math.round(
									Math.min(CHARACTER_WIDTH * widthOfHeader + AUTO_FILTER_WIDTH, COLUMN_MAX_WIDTH));
							if (intWidth > sheet.getColumnWidth(columnIndex)) {
								sheet.setColumnWidth(columnIndex, intWidth);
							}
						}
					}
				});

				workbook.write(outputStream);
			}
		}

		@SuppressWarnings("checkstyle:MagicNumber")
		private void writeProducts(final XSSFSheet sheet) {
			final Set<Product> products = Budgets.getProducts(budgets);
			appendProducts(sheet, products);
			appendProductBudgets(sheet, products);

			if (sheet.getLastRowNum() > 0) {
				// Table
				sheet.createFreezePane(3, 0);
				final XSSFTable table = createTable(sheet, SHEET_NAME_PRODUCTS);

				// Totals Row
				table.setDataRowCount(table.getDataRowCount() + 1);
				appendProductsTotalsRow(appendRow(sheet), table.getCTTable());
			}
		}

		private void appendProducts(final Sheet sheet, final Set<Product> products) {
			// Headers
			appendStrings(appendRow(
					sheet), COLUMN_NAME_MUNICIPALITY, COLUMN_NAME_PRODUCT, COLUMN_NAME_DESCRIPTION, COLUMN_NAME_SUM);

			// Values
			for (final Product product : products) {
				final Row row = appendRow(sheet);
				appendNumber(row, OptionalInt.of(product.getMunicipality().getId()));
				appendNumber(row, OptionalInt.of(product.getId()));
				appendString(row, product.getDescription());
				appendBoolean(row, true);
			}
		}

		private void appendProductBudgets(final XSSFSheet sheet, final Set<Product> products) {
			for (final Budget budget : budgets) {
				// Header
				final Cell headerCell
						= appendString(sheet.getRow(0), getSimplifiedCellStyle(budget), getBudgetColumnName(budget));
				addBudgetsComment(headerCell, budget);

				// Balances
				int rowIndex = 1;
				for (int i = products.size(); i > 0; i -= 1) {
					appendFormula(sheet.getRow(rowIndex),
							getSimplifiedCellStyle(budget),
							DATA_FORMAT_CURRENCY,
							String.format(
									"SUMIFS(%1$s[%7$s], "
											+ "%1$s[%3$s], %2$s[[#This Row],[%3$s]], "
											+ "%1$s[%4$s], %2$s[[#This Row],[%5$s]], "
											+ "%1$s[%6$s], TRUE)",
									SHEET_NAME_ACCOUNTS, // 1
									SHEET_NAME_PRODUCTS, // 2
									COLUMN_NAME_PRODUCT, // 3
									COLUMN_NAME_PRODUCT_DESCRIPTION, // 4
									COLUMN_NAME_DESCRIPTION, // 5
									COLUMN_NAME_SUM, // 6
									getBudgetColumnName(budget))); // 7
					rowIndex += 1;
				}
			}
		}

		private void appendProductsTotalsRow(final Row row, final CTTable table) {
			final Iterator<CTTableColumn> columns = appendTotalsRow(row, table, 4);
			appendSumsForBudgets(row, columns);
		}

		private void writeAccounts(final XSSFSheet sheet) {
			final Set<Account> accounts = Budgets.getAccounts(budgets);
			appendAccounts(sheet, accounts);
			appendAccountBudgets(sheet, accounts);

			if (sheet.getLastRowNum() > 0) {
				// Table
				final XSSFTable table = createTable(sheet, SHEET_NAME_ACCOUNTS);

				// Totals Row
				table.setDataRowCount(table.getDataRowCount() + 1);
				appendAccountsTotalsRow(appendRow(sheet), table.getCTTable());
			}
		}

		private void appendAccounts(final Sheet sheet, final Set<Account> accounts) {
			// Headers
			final Row headerRow = appendRow(sheet);
			appendStrings(headerRow,
					COLUMN_NAME_MUNICIPALITY,
					COLUMN_NAME_PRODUCT,
					COLUMN_NAME_PRODUCT_DESCRIPTION,
					COLUMN_NAME_ACCOUNT,
					COLUMN_NAME_ACCOUNT_DESCRIPTION,
					COLUMN_NAME_SUM);

			sheet.setColumnHidden(appendString(headerRow, CsvFiles.COLUMN_NAME_MUNICIPALITY).getColumnIndex(), true);
			sheet.setColumnHidden(appendString(headerRow, CsvFiles.COLUMN_NAME_PRODUCT_ID).getColumnIndex(), true);
			sheet.setColumnHidden(appendString(headerRow, CsvFiles.COLUMN_NAME_PRODUCT_DESCRIPTION).getColumnIndex(),
					true);
			sheet.setColumnHidden(appendString(headerRow, CsvFiles.COLUMN_NAME_ACCOUNT).getColumnIndex(), true);

			// Values
			for (final Account account : accounts) {
				final Row row = appendRow(sheet);
				appendNumber(row, OptionalInt.of(account.getProduct().getMunicipality().getId()));
				appendNumber(row, OptionalInt.of(account.getProduct().getId()));
				appendString(row, account.getProduct().getDescription());
				appendNumber(row, OptionalInt.of(account.getId()));
				appendString(row, account.getDescription());
				appendBoolean(row, true);

				appendNumber(row, OptionalInt.of(account.getProduct().getMunicipality().getId()));
				appendNumber(row, OptionalInt.of(account.getProduct().getId()));
				appendString(row, account.getProduct().getDescription());
				appendString(row, String.format("%d %s", account.getId(), account.getDescription()));
			}
		}

		private void appendAccountBudgets(final XSSFSheet sheet, final Set<Account> accounts) {
			for (final Budget budget : budgets) {
				// Header
				final Cell headerCell
						= appendString(sheet.getRow(0), getSimplifiedCellStyle(budget), getBudgetColumnName(budget));
				addBudgetsComment(headerCell, budget);

				// Balances
				int rowIndex = 1;
				final Map<Account, Balance> balances = budget.getBalances();
				for (final Account account : accounts) {
					final Optional<Balance> balance = Optional.ofNullable(balances.get(account));

					appendNumber(sheet.getRow(rowIndex),
							getSimplifiedCellStyle(budget),
							DATA_FORMAT_CURRENCY,
							balance.map(Balance::getValue));
					rowIndex += 1;
				}
			}
		}

		private void appendAccountsTotalsRow(final Row row, final CTTable table) {
			final Iterator<CTTableColumn> columns = appendTotalsRow(row, table, 10);
			appendSumsForBudgets(row, columns);
		}

		private void appendSumsForBudgets(final Row row, final Iterator<CTTableColumn> columnsIterator) {
			for (final Budget budget : budgets) {
				columnsIterator.next().setTotalsRowFunction(STTotalsRowFunction.CUSTOM);
				appendFormula(row,
						getSimplifiedCellStyle(budget),
						DATA_FORMAT_CURRENCY,
						String.format("SUMIFS(%1$s[%3$s], %1$s[%2$s], TRUE)",
								SHEET_NAME_PRODUCTS,
								COLUMN_NAME_SUM,
								getBudgetColumnName(budget)));
			}
		}
	}

	@SuppressWarnings("PMD.UnnecessaryModifier")
	private enum SimplifiedCellStyle {
		BOLD,
		ITALIC,
		NORMAL;
	}
}
