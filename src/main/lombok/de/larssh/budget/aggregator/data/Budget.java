package de.larssh.budget.aggregator.data;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.larssh.utils.Finals;
import de.larssh.utils.text.Csv;
import de.larssh.utils.text.CsvRow;
import de.larssh.utils.text.Patterns;
import de.larssh.utils.text.StringParseException;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * In German: Haushalt
 */
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Budget implements Comparable<Budget> {
	private static final Pattern BUDGET_HEADER_PATTERN
			= Pattern.compile("^\\s*(?<budgetType>.+?)\\s*((?<year>\\d+)|(?<yearBefore>Vorjahr))?\\s*$");

	private static final Comparator<Budget> COMPARATOR
			= Comparator.<Budget>comparingInt(Budget::getYear).thenComparing(Budget::getType);

	public static final char CSV_SEPARATOR = Finals.constant('\t');

	public static final char CSV_ESCAPER = Finals.constant('"');

	public static final String CSV_HEADER_MUNICIPALITY = Finals.constant("GKZ");

	private static final String CSV_HEADER_BUDGET_YEAR = Finals.constant("HHJ");

	public static final String CSV_HEADER_PRODUCT_ID = Finals.constant("Budget");

	public static final String CSV_HEADER_PRODUCT_DESCRIPTION = Finals.constant("Bezeichnung Budget");

	public static final String CSV_HEADER_ACCOUNT = Finals.constant("Bezeichnung Position");

	public static Set<Budget> of(final Csv csv) throws StringParseException {
		final int lastNonBalanceColumn = csv.getHeaders().indexOf(CSV_HEADER_ACCOUNT);
		if (lastNonBalanceColumn == -1) {
			return emptySet();
		}

		final Map<Budget, Budget> budgets = new LinkedHashMap<>();
		for (final CsvRow row : csv.subList(1, csv.size())) {
			try {
				final Optional<Account> account = Account.of(row);
				if (account.isPresent()) {
					final int headerSize = row.getCsv().getHeaders().size();
					for (int column = lastNonBalanceColumn + 1; column < headerSize; column += 1) {
						addBalance(budgets, account.get(), row, column);
					}
				}
			} catch (final Exception e) {
				throw new StringParseException(e, "Failed reading row %d.", row.getRowIndex());
			}
		}
		return budgets.keySet();
	}

	private static void addBalance(final Map<Budget, Budget> budgets,
			final Account account,
			final CsvRow row,
			final int column) {
		final String title = row.getCsv().getHeaders().get(column);
		final Optional<Matcher> columnHeaderMatcher = Patterns.matches(BUDGET_HEADER_PATTERN, title);
		if (!columnHeaderMatcher.isPresent()) {
			return;
		}

		final OptionalInt year = determineYear(row, columnHeaderMatcher.get());
		if (!year.isPresent()) {
			return;
		}

		final BudgetType budgetType = BudgetType.of(columnHeaderMatcher.get().group("budgetType"));
		final Budget budget = new Budget(year.getAsInt(), budgetType);
		final Balance balance = new Balance(account, determineValue(account, row, column), "");
		budgets.computeIfAbsent(budget, Function.identity()).balances.put(account, balance);
	}

	private static OptionalInt determineYear(final CsvRow row, final Matcher columnHeaderMatcher) {
		final String year = columnHeaderMatcher.group("year");
		if (year != null) {
			return OptionalInt.of(Integer.parseInt(year));
		}

		final Optional<String> yearCell = row.get(CSV_HEADER_BUDGET_YEAR);
		if (!yearCell.isPresent() || Strings.isBlank(yearCell.get())) {
			return OptionalInt.empty();
		}

		final int offsetYears = columnHeaderMatcher.group("yearBefore") == null ? 0 : -1;
		return OptionalInt.of(Integer.parseInt(yearCell.get()) + offsetYears);
	}

	private static BigDecimal determineValue(final Account account, final CsvRow row, final int column) {
		final BigDecimal value = new BigDecimal(row.get(column));
		return account.getType().getSign() < 0 ? value.negate() : value;
	}

	@EqualsAndHashCode.Include
	int year;

	@EqualsAndHashCode.Include
	BudgetType type;

	@ToString.Exclude
	Map<Account, Balance> balances = new TreeMap<>();

	// TODO: Probably it makes sense to store the source here (file, sheet, column)

	@Override
	public int compareTo(@Nullable final Budget other) {
		return COMPARATOR.compare(this, other);
	}

	public boolean equalsIncludingBalances(final Budget other) {
		return equals(other) && containsBalances(other) && other.containsBalances(this);
	}

	private boolean containsBalances(final Budget other) {
		for (final Entry<Account, Balance> entry : other.getBalances().entrySet()) {
			final BigDecimal otherValue = entry.getValue().getValue();

			if (otherValue.compareTo(BigDecimal.ZERO) != 0) {
				final Balance thisBalance = getBalances().get(entry.getKey());
				if (thisBalance != null && thisBalance.getValue().compareTo(otherValue) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	public Map<Account, Balance> getBalances() {
		return unmodifiableMap(balances);
	}
}
