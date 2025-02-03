package de.larssh.budget.aggregator.cli;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toSet;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import de.larssh.budget.aggregator.data.Account;
import de.larssh.budget.aggregator.data.Balance;
import de.larssh.budget.aggregator.data.Budget;
import de.larssh.budget.aggregator.data.BudgetType;
import de.larssh.budget.aggregator.utils.CellValues;
import de.larssh.utils.Nullables;
import de.larssh.utils.io.Resources;
import de.larssh.utils.text.Csv;
import de.larssh.utils.text.Patterns;
import de.larssh.utils.text.StringParseException;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The CLI interface for {@link BudgetAggregator}
 */
@Getter
@RequiredArgsConstructor
@SuppressWarnings("PMD.ExcessiveImports")
@Command(name = "budget-aggregator",
		mixinStandardHelpOptions = true,
		showDefaultValues = true,
		usageHelpWidth = 160,
		versionProvider = BudgetAggregatorCli.class,
		description = "TODO")
public class BudgetAggregatorCli implements Callable<Integer>, IVersionProvider {
	private static final DecimalFormat DECIMAL_FORMAT
			= (DecimalFormat) NumberFormat.getCurrencyInstance(Locale.GERMANY);
	static {
		DECIMAL_FORMAT.setPositiveSuffix(" €");
		DECIMAL_FORMAT.setNegativeSuffix(" €");
	}

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
	@Option(names = "--filter-budget-types", converter = BudgetTypeConverter.class)
	Set<BudgetType> filterBudgetTypes = emptySet();

	@NonFinal
	@Option(names = "--filter-years", converter = YearsConverter.class)
	final Set<Integer> filterYears = emptySet();

	@NonFinal
	@Option(names = "--hide-duplicate-budgets", defaultValue = "true", fallbackValue = "true", negatable = true)
	boolean hideDuplicateBudgets;

	@NonFinal
	@Option(names = "--hide-empty-accounts", defaultValue = "true", fallbackValue = "true", negatable = true)
	boolean hideEmptyAccounts;

	@NonFinal
	@Option(names = "--hide-empty-balances", defaultValue = "true", fallbackValue = "true", negatable = true)
	boolean hideEmptyBalances;

	@NonFinal
	@Option(names = "--hide-empty-budgets", defaultValue = "true", fallbackValue = "true", negatable = true)
	boolean hideEmptyBudgets;

	@NonFinal
	@Option(names = "--open-output", defaultValue = "false", negatable = true)
	boolean openOutput;

	@NonFinal
	@Parameters(index = "0", descriptionKey = "SOURCES")
	List<Path> sources = emptyList();

	@NonFinal
	@Nullable
	@Parameters(index = "1", descriptionKey = "DESTINATION", arity = "0..1")
	Path destination = null;

	private static final Pattern CSV_FILE_EXTENSION_PATTERN = Pattern.compile("(?i)\\.csv$");

	@Override
	public Integer call() throws IOException, StringParseException {
		final List<Budget> budgets = readBudgets();

		final Set<Account> accounts = new TreeSet<>();
		for (final Budget budget : budgets) {
			accounts.addAll(budget.getBalances().keySet());
		}

		final Csv csv = new Csv(singletonList(new ArrayList<>(Arrays.asList(Budget.CSV_HEADER_MUNICIPALITY,
				Budget.CSV_HEADER_PRODUCT_ID,
				Budget.CSV_HEADER_PRODUCT_DESCRIPTION,
				Budget.CSV_HEADER_ACCOUNT))));
		for (final Account account : accounts) {
			csv.add(new ArrayList<>(Arrays.asList( //
					Integer.toString(account.getProduct().getMunicipality().getId()),
					Integer.toString(account.getProduct().getId()),
					account.getProduct().getDescription(),
					String.format("%d %s", account.getId(), account.getDescription()))));
		}

		for (final Budget budget : budgets) {
			csv.get(0).add(String.format("%s %d", budget.getType().getName(), budget.getYear()));

			int rowIndex = 1;
			final Map<Account, Balance> balances = budget.getBalances();
			for (final Account account : accounts) {
				final Balance balance = balances.get(account);
				csv.get(rowIndex).add(balance == null ? "" : DECIMAL_FORMAT.format(balance.getValue()));
				rowIndex += 1;
			}
		}
		System.out.println(csv.toString(Budget.CSV_SEPARATOR, Budget.CSV_ESCAPER));

		if (isOpenOutput() && Desktop.isDesktopSupported()) {
			Desktop.getDesktop().open(getDestination().toFile());
		}

		return ExitCode.OK;
	}

	private List<Budget> readBudgets() throws IOException, StringParseException {
		final List<Budget> budgets = new ArrayList<>();
		for (final Path source : getSources()) {
			for (final Csv sheet : read(source)) {
				budgets.addAll(Budget.of(sheet));
			}
		}

		if (!getFilterBudgetTypes().isEmpty()) {
			budgets.removeIf(budget -> !getFilterBudgetTypes().contains(budget.getType()));
		}
		if (!getFilterYears().isEmpty()) {
			budgets.removeIf(budget -> !getFilterYears().contains(budget.getYear()));
		}
		if (isHideEmptyAccounts()) {
			removeEmptyAccounts(budgets);
		}
		if (isHideEmptyBalances()) {
			budgets.forEach(Budget::removeEmptyBalances);
		}
		if (isHideEmptyBudgets()) {
			removeEmptyBudgets(budgets);
		}

		sort(budgets);
		if (isHideDuplicateBudgets()) {
			removeDuplicateBudgets(budgets);
		}

		return budgets;
	}

	private void removeDuplicateBudgets(final List<Budget> budgets) {
		// Prerequisite: budgets must be sorted!
		int index = 1;
		while (index < budgets.size()) {
			if (budgets.get(index).equalsIncludingBalances(budgets.get(index - 1))) {
				budgets.remove(index);
			} else {
				index += 1;
			}
		}
	}

	private void removeEmptyAccounts(final List<Budget> budgets) {
		// Get all accounts
		final Set<Account> accounts = budgets.stream() //
				.flatMap(budget -> budget.getBalances().keySet().stream())
				.collect(toSet());

		for (final Account account : accounts) {
			// Check if current account is empty
			final boolean isEmptyAccount = budgets.stream()
					.map(budget -> budget.getBalances().get(account))
					.allMatch(balance -> balance == null || balance.getValue().compareTo(BigDecimal.ZERO) == 0);

			// Remove account if empty
			if (isEmptyAccount) {
				for (final Budget budget : budgets) {
					budget.getBalances().remove(account);
				}
			}
		}
	}

	private void removeEmptyBudgets(final List<Budget> budgets) {
		budgets.removeIf(budget -> budget.getBalances()
				.values()
				.stream()
				.allMatch(balance -> balance.getValue().compareTo(BigDecimal.ZERO) == 0));
	}

	private List<Csv> read(final Path path) throws IOException {
		return Patterns.find(CSV_FILE_EXTENSION_PATTERN, path.toString()).isPresent()
				? Arrays.asList(readCsv(path))
				: readExcel(path);
	}

	private Csv readCsv(final Path source) throws IOException {
		try (Reader reader = Files.newBufferedReader(source)) {
			return Csv.parse(reader, Budget.CSV_SEPARATOR, Budget.CSV_ESCAPER);
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

	private CommandSpec getCommandSpec() {
		return Nullables.orElseThrow(commandSpec);
	}

	private synchronized Path getDestination() throws IOException {
		if (destination == null) {
			destination = Files.createTempFile("budget-aggregator", ".csv");
		}
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
		return getCommandSpec().commandLine().getOut();
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
		filterBudgetTypes = emptySet();
		sources = emptyList();
		destination = null;
	}
}
