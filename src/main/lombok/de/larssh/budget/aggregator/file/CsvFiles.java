package de.larssh.budget.aggregator.file;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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

import de.larssh.budget.aggregator.data.Account;
import de.larssh.budget.aggregator.data.Balance;
import de.larssh.budget.aggregator.data.Budget;
import de.larssh.budget.aggregator.data.BudgetReference;
import de.larssh.budget.aggregator.data.Budgets;
import de.larssh.utils.Finals;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.text.Csv;
import de.larssh.utils.text.StringParseException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CsvFiles {
	public static final char SEPARATOR = Finals.constant('\t');

	public static final char ESCAPER = Finals.constant('"');

	public static final String HEADER_MUNICIPALITY = Finals.constant("GKZ");

	public static final String HEADER_BUDGET_YEAR = Finals.constant("HHJ");

	public static final String HEADER_PRODUCT_ID = Finals.constant("Budget");

	public static final String HEADER_PRODUCT_DESCRIPTION = Finals.constant("Bezeichnung Budget");

	public static final String HEADER_ACCOUNT = Finals.constant("Bezeichnung Position");

	public static Set<Budget> read(final Path source) throws IOException, StringParseException {
		try (Reader reader = Files.newBufferedReader(source)) {
			final Set<Budget> budgets = Budget.of(Csv.parse(reader, SEPARATOR, ESCAPER));
			Budgets.setReference(budgets, BudgetReference.FILE_NAME, source.getFileName().toString());
			return budgets;
		}
	}

	public static void write(final List<Budget> budgets, final Writer writer) throws IOException {
		new CsvFileWriter(budgets, writer).write();
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static class CsvFileWriter {
		private static final ThreadLocal<NumberFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
			final DecimalFormat format = (DecimalFormat) NumberFormat.getCurrencyInstance(Locale.GERMANY);
			format.setPositiveSuffix(" €");
			format.setNegativeSuffix(" €");
			return format;
		});

		List<Budget> budgets;

		Writer writer;

		@PackagePrivate
		void write() throws IOException {
			final Set<Account> accounts = Budgets.getAccounts(budgets);

			final Csv csv = new Csv();
			appendAccounts(csv, accounts);
			appendBudgets(csv, accounts);
			writer.write(csv.toString(SEPARATOR, ESCAPER));
		}

		private void appendAccounts(final Csv csv, final Set<Account> accounts) {
			// Headers
			csv.add(new ArrayList<>(
					Arrays.asList(HEADER_MUNICIPALITY, HEADER_PRODUCT_ID, HEADER_PRODUCT_DESCRIPTION, HEADER_ACCOUNT)));

			// Values
			for (final Account account : accounts) {
				csv.add(new ArrayList<>(Arrays.asList( //
						Integer.toString(account.getProduct().getMunicipality().getId()),
						Integer.toString(account.getProduct().getId()),
						account.getProduct().getDescription(),
						String.format("%d %s", account.getId(), account.getDescription()))));
			}
		}

		private void appendBudgets(final Csv csv, final Set<Account> accounts) {
			for (final Budget budget : budgets) {
				// Header
				csv.get(0).add(String.format("%s %d", budget.getType().getName(), budget.getYear()));

				// Balances
				int rowIndex = 1;
				final Map<Account, Balance> balances = budget.getBalances();
				for (final Account account : accounts) {
					final Balance balance = balances.get(account);
					csv.get(rowIndex).add(balance == null ? "" : DECIMAL_FORMAT.get().format(balance.getValue()));
					rowIndex += 1;
				}
			}
		}
	}
}
