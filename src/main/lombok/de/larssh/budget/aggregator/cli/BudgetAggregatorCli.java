package de.larssh.budget.aggregator.cli;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableList;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;

import org.apache.poi.poifs.filesystem.FileMagic;

import de.larssh.budget.aggregator.data.Budget;
import de.larssh.budget.aggregator.data.BudgetType;
import de.larssh.budget.aggregator.data.Budgets;
import de.larssh.budget.aggregator.sheets.csv.CsvFiles;
import de.larssh.budget.aggregator.sheets.excel.ExcelFiles;
import de.larssh.utils.Nullables;
import de.larssh.utils.io.Resources;
import de.larssh.utils.text.StringParseException;
import de.larssh.utils.text.Strings;
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
 * The CLI interface of the Budget Aggregator
 */
@Getter
@RequiredArgsConstructor
@SuppressWarnings({ "PMD.DataClass", "PMD.ExcessiveImports" })
@Command(name = "budget-aggregator",
		mixinStandardHelpOptions = true,
		showDefaultValues = true,
		usageHelpWidth = 160,
		versionProvider = BudgetAggregatorCli.class)
public class BudgetAggregatorCli implements Callable<Integer>, IVersionProvider {
	/**
	 * The CLI interface of the Budget Aggregator
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
	CommandSpec commandSpec;

	@NonFinal
	@Parameters(descriptionKey = "SOURCES")
	List<Path> sources = emptyList();

	@NonFinal
	@Option(names = "--output")
	Path output = Paths.get("");

	@NonFinal
	@Option(names = "--filter-budget-types", converter = BudgetTypeConverter.class)
	Set<BudgetType> filterBudgetTypes = emptySet();

	@NonFinal
	@Option(names = "--filter-years", converter = YearsConverter.class)
	Set<Integer> filterYears = emptySet();

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
	@Option(names = "--reverse-budgets", defaultValue = "false", negatable = true)
	boolean reverseBudgets;

	@Override
	public Integer call() throws IOException, StringParseException {
		final List<Budget> budgets = new ArrayList<>();
		for (final Path source : getSources()) {
			budgets.addAll(readSource(source));
		}

		applyFiltersAndHide(budgets);
		sortAndHideDuplicates(budgets);
		writeOutput(budgets);
		openFile();

		return ExitCode.OK;
	}

	private List<Budget> readSource(final Path path) throws IOException, StringParseException {
		return FileMagic.valueOf(path.toFile()) == FileMagic.UNKNOWN //
				? CsvFiles.read(path)
				: ExcelFiles.read(path);
	}

	private void applyFiltersAndHide(final List<Budget> budgets) {
		// Apply Filters
		if (!getFilterBudgetTypes().isEmpty()) {
			budgets.removeIf(budget -> !getFilterBudgetTypes().contains(budget.getType()));
		}
		if (!getFilterYears().isEmpty()) {
			budgets.removeIf(budget -> !getFilterYears().contains(budget.getYear()));
		}

		// Hide Empty Accounts/Balances/Budgets
		if (isHideEmptyAccounts()) {
			Budgets.removeEmptyAccounts(budgets);
		}
		if (isHideEmptyBalances()) {
			budgets.forEach(Budget::removeEmptyBalances);
		}
		if (isHideEmptyBudgets()) {
			Budgets.removeEmptyBudgets(budgets);
		}
	}

	private void sortAndHideDuplicates(final List<Budget> budgets) {
		// Sort Budgets
		sort(budgets);

		// Hide Duplicate Budgets (requires sorted budgets!)
		if (isHideDuplicateBudgets()) {
			Budgets.removeDuplicateBudgets(budgets);
		}

		// Apply Budget Order
		if (isReverseBudgets()) {
			Collections.reverse(budgets);
		}
	}

	@SuppressWarnings("PMD.CloseResource")
	private void writeOutput(final List<Budget> budgets) throws IOException {
		if (hasOutput() || isOpenOutput()) {
			if (Strings.endsWithIgnoreCaseAscii(getOutput().toString(), ".csv")) {
				try (Writer writer = Files.newBufferedWriter(getOutput())) {
					CsvFiles.write(budgets, writer);
				}
			} else {
				try (OutputStream outputStream = Files.newOutputStream(getOutput())) {
					ExcelFiles.write(budgets, outputStream);
				}
			}

			@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
			final PrintWriter writer = getStandardOutputWriter();
			writer.println(String.format("Output written to \"%s\".", getOutput()));
		} else {
			@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
			final Writer writer = getStandardOutputWriter();
			CsvFiles.write(budgets, writer);
			writer.flush();
		}
	}

	private void openFile() throws IOException {
		if (isOpenOutput() && Desktop.isDesktopSupported()) {
			Desktop.getDesktop().open(getOutput().toFile());
		}
	}

	private CommandSpec getCommandSpec() {
		return Nullables.orElseThrow(commandSpec);
	}

	private Path getOutput() throws IOException {
		if (Strings.isBlank(output.toString())) {
			output = Files.createTempFile(getClass().getSimpleName() + "-", ".xlsx");
		}
		return output;
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

	private boolean hasOutput() {
		return !Strings.isBlank(output.toString());
	}

	/**
	 * Dummy to avoid the IDE to mark some fields as {@code final}.
	 */
	@SuppressWarnings({ "PMD.NullAssignment", "PMD.UnusedPrivateMethod" })
	@SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "dummy method")
	private void nonFinalDummy() {
		commandSpec = null;
		filterBudgetTypes = emptySet();
		filterYears = emptySet();
		sources = emptyList();
	}
}
