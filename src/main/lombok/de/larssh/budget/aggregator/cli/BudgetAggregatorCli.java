package de.larssh.budget.aggregator.cli;

import static java.util.Collections.emptyList;
import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import de.larssh.budget.aggregator.data.Budget;
import de.larssh.budget.aggregator.utils.CellValues;
import de.larssh.utils.Nullables;
import de.larssh.utils.io.Resources;
import de.larssh.utils.text.Csv;
import de.larssh.utils.text.Patterns;
import de.larssh.utils.text.StringParseException;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The CLI interface for {@link BudgetAggregator}
 */
@RequiredArgsConstructor
@SuppressWarnings("PMD.ExcessiveImports")
@Command(name = "budget-aggregator",
		mixinStandardHelpOptions = true,
		showDefaultValues = true,
		usageHelpWidth = 160,
		versionProvider = BudgetAggregatorCli.class,
		description = "TODO")
public class BudgetAggregatorCli implements Callable<Integer>, IVersionProvider {
	/**
	 * The CLI interface for {@link LocalElectionResult}.
	 *
	 * @param args CLI arguments
	 */
	@SuppressWarnings("checkstyle:UncommentedMain")
	public static void main(final String... args) {
		System.exit(new CommandLine(new BudgetAggregatorCli()).execute(args));
	}

	/**
	 * Current {@link CommandSpec} instance
	 */
	@Spec
	@NonFinal
	@Nullable
	CommandSpec commandSpec = null;

	@NonFinal
	@Parameters(index = "0", descriptionKey = "SOURCES") // TODO: description
	List<Path> sources = emptyList();

	@NonFinal
	@Nullable
	@Parameters(index = "1", descriptionKey = "DESTINATION") // TODO: description
	Path destination = null;

	private static final Pattern CSV_FILE_EXTENSION_PATTERN = Pattern.compile("(?i)\\.csv$");

	@Override
	public Integer call() throws IOException, StringParseException {
		final List<Budget> budgets = readBudgets();
		System.out.println(budgets);

		// final List<Content> mergedContents = mergeContents(contents); final
		// Set<Header> collectedKeyHeaders = collectKeyHeaders(mergedContents); final
		// Map<Sheet, Set<Header>> collectedValueHeaders =
		// collectValueHeaders(mergedContents); // Print Headers for (final Header
		// header : collectedKeyHeaders) {
		// getStandardOutputWriter().print(CellValues.getAsString(CellValues.create(header.getCell(),
		// true))); getStandardOutputWriter().print("\t"); } for (final Entry<Sheet,
		// Set<Header>> entry : collectedValueHeaders.entrySet()) { for (final Header
		// header : entry.getValue()) {
		// getStandardOutputWriter().print(entry.getKey().getSheetName() + " " +
		// CellValues.getAsString(CellValues.create(header.getCell(), true)));
		// getStandardOutputWriter().print("\t"); } }
		// getStandardOutputWriter().println(); // Print Values for (final Content
		// content : mergedContents) { for (final Header header : collectedKeyHeaders) {
		// final Cell value = content.getKeys().get(header); getStandardOutputWriter()
		// .print(value == null ? "" : CellValues.getAsString(CellValues.create(value,
		// true))); getStandardOutputWriter().print("\t"); } for (final Entry<Sheet,
		// Set<Header>> entry : collectedValueHeaders.entrySet()) { final Map<Header,
		// Cell> values = content.getValues().get(entry.getKey()); for (final Header
		// header : entry.getValue()) { final Cell value = Nullables.map(values, v ->
		// v.get(header)); getStandardOutputWriter() .print(value == null ? "" :
		// CellValues.getAsString(CellValues.create(value, true)));
		// getStandardOutputWriter().print("\t"); } }
		// getStandardOutputWriter().println(); }

		return ExitCode.OK;
	}

	private List<Budget> readBudgets() throws IOException, StringParseException {
		final List<Budget> budgets = new ArrayList<>();
		for (final Path source : getSources()) {
			for (final Csv sheet : read(source)) {
				budgets.addAll(Budget.of(sheet));
			}
		}
		sort(budgets);
		return budgets;
	}

	private List<Csv> read(final Path path) throws IOException {
		return Patterns.find(CSV_FILE_EXTENSION_PATTERN, path.toString()).isPresent()
				? Arrays.asList(readCsv(path))
				: readExcel(path);
	}

	private Csv readCsv(final Path source) throws IOException {
		try (Reader reader = Files.newBufferedReader(source)) {
			return Csv.parse(reader, '\t', Csv.DEFAULT_ESCAPER);
		}
	}

	private List<Csv> readExcel(final Path source) throws IOException {
		final List<Csv> sheets = new ArrayList<>();
		try (InputStream inputStream = Files.newInputStream(source);
				Workbook workbook = WorkbookFactory.create(inputStream)) {
			final int numberOfSheets = workbook.getNumberOfSheets();
			for (int sheetIndex = 0; sheetIndex < numberOfSheets; sheetIndex += 1) {
				sheets.add(readSheet(workbook.getSheetAt(sheetIndex)));
			}
		}
		return sheets;
	}

	private Csv readSheet(final Sheet sheet) {
		final List<List<String>> data = new ArrayList<>();
		final int lastRowIndex = sheet.getLastRowNum();
		for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRowIndex; rowIndex += 1) {
			data.add(readRow(sheet.getRow(rowIndex)));
		}
		return new Csv(data);
	}

	private List<String> readRow(final Row row) {
		final List<String> cells = new ArrayList<>();
		final int lastCellIndex = row.getLastCellNum();
		for (int cellIndex = row.getFirstCellNum(); cellIndex <= lastCellIndex; cellIndex += 1) {
			cells.add(CellValues.getAsString(CellValues.create(row.getCell(cellIndex), true)));
		}
		return cells;
	}

	private Path getDestination() {
		return Nullables.orElseThrow(destination);
	}

	private List<Path> getSources() {
		return unmodifiableList(sources);
	}

	/**
	 * Returns the standard output writer based on the current {@link CommandSpec}.
	 *
	 * @return the standard output writer
	 */
	private PrintWriter getStandardOutputWriter() {
		return Nullables.orElseThrow(commandSpec).commandLine().getOut();
	}

	/** {@inheritDoc} */
	@Override
	public String[] getVersion() throws IOException {
		return new String[] {
				Resources.readManifest(getClass())
						.map(manifest -> manifest.getMainAttributes().get(Name.IMPLEMENTATION_VERSION).toString())
						.orElse("unknown") };
	}

	/**
	 * Dummy to avoid the IDE to mark some fields as {@code final}.
	 */
	@SuppressWarnings({ "PMD.NullAssignment", "PMD.UnusedPrivateMethod" })
	@SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "dummy method")
	private void nonFinalDummy() {
		commandSpec = null;
		destination = null;
		sources = emptyList();
	}
}
