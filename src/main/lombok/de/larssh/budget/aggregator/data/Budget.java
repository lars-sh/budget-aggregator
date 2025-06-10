package de.larssh.budget.aggregator.data;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
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

import org.apache.poi.ss.util.CellReference;

import de.larssh.budget.aggregator.sheets.Row;
import de.larssh.budget.aggregator.sheets.Sheet;
import de.larssh.budget.aggregator.sheets.csv.CsvFiles;
import de.larssh.utils.text.Patterns;
import de.larssh.utils.text.StringParseException;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.NonFinal;

/**
 * In German: Haushalt
 */
@Getter
@ToString
@SuppressWarnings("PMD.GodClass")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Budget implements Comparable<Budget> {
	private static final Pattern BUDGET_HEADER_PATTERN
			= Pattern.compile("^\\s*(?<budgetType>.+?)\\s*((?<year>\\d+)|(?<yearBefore>Vorjahr))?\\s*$");

	private static final Comparator<Budget> COMPARATOR
			= Comparator.<Budget>comparingInt(Budget::getYear).thenComparing(Budget::getType);

	@SuppressWarnings({ "checkstyle:XIllegalCatchDefault", "PMD.AvoidCatchingGenericException", "PMD.ShortMethodName" })
	@SuppressFBWarnings(value = "WEM_WEAK_EXCEPTION_MESSAGING",
			justification = "false-positive, using StringFormatter here")
	public static Set<Budget> of(final Sheet sheet) throws StringParseException {
		final int lastNonBalanceColumn = sheet.getHeader().indexOf(CsvFiles.COLUMN_NAME_ACCOUNT);
		if (lastNonBalanceColumn == -1) {
			return emptySet();
		}

		final boolean applyBudgetTypeSign = sheet.isApplyBudgetTypeSign();
		final Map<Budget, Budget> budgets = new LinkedHashMap<>();
		for (final Row row : sheet.getRows()) {
			try {
				final Optional<Account> account = Account.of(row);
				if (account.isPresent()) {
					final boolean negate = applyBudgetTypeSign && account.get().getType().getSign() < 0;

					final int headerSize = sheet.getHeader().size();
					for (int columnIndex = lastNonBalanceColumn + 1; columnIndex < headerSize; columnIndex += 1) {
						addBalance(budgets, account.get(), negate, row, sheet, columnIndex);
					}
				}
			} catch (final Exception e) {
				throw new StringParseException(e, "Failed reading row %d.", row.getRowIndex());
			}
		}
		return budgets.keySet();
	}

	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private static void addBalance(final Map<Budget, Budget> budgets,
			final Account account,
			final boolean negate,
			final Row row,
			final Sheet sheet,
			final int columnIndex) {
		final String cellValue = row.get(columnIndex).orElse(null);
		if (Strings.isBlank(cellValue)) {
			return;
		}

		final Optional<Matcher> columnHeaderMatcher = Optional.ofNullable(sheet.getHeader().get(columnIndex))
				.flatMap(title -> Patterns.matches(BUDGET_HEADER_PATTERN, title));
		if (!columnHeaderMatcher.isPresent()) {
			return;
		}

		final OptionalInt year = determineYear(row, columnHeaderMatcher.get());
		if (!year.isPresent()) {
			return;
		}

		final BudgetType budgetType = BudgetType.of(columnHeaderMatcher.get().group("budgetType"));
		final Budget newBudget = new Budget(year.getAsInt(), budgetType);
		final Budget budget = budgets.computeIfAbsent(newBudget, Function.identity());

		final Balance balance = new Balance(account, determineValue(cellValue, negate));
		budget.balances.put(account, balance);

		// Add References
		if (budget == newBudget) {
			sheet.getHeaderReferences()
					.get(columnIndex)
					.entrySet()
					.forEach(entry -> budget.setReferenceIfAbsent(entry.getKey(), entry.getValue()));
		}
		row.get(CsvFiles.COLUMN_NAME_BUDGET_YEAR)
				.ifPresent(y -> budget.setReferenceIfAbsent(BudgetReference.BUDGET_YEAR, y));
		budget.setReferenceIfAbsent(BudgetReference.COLUMN, CellReference.convertNumToColString(columnIndex));
	}

	@SuppressFBWarnings(value = "OCP_OVERLY_CONCRETE_PARAMETER", justification = "only valid for Java 20 and later")
	private static OptionalInt determineYear(final Row row, final Matcher columnHeaderMatcher) {
		final String year = columnHeaderMatcher.group("year");
		if (year != null) {
			return OptionalInt.of(Integer.parseInt(year));
		}

		final Optional<String> yearCell = row.get(CsvFiles.COLUMN_NAME_BUDGET_YEAR);
		if (!yearCell.isPresent() || Strings.isBlank(yearCell.get())) {
			return OptionalInt.empty();
		}

		final int offsetYears = columnHeaderMatcher.group("yearBefore") == null ? 0 : -1;
		return OptionalInt.of(Integer.parseInt(yearCell.get()) + offsetYears);
	}

	private static BigDecimal determineValue(final String cellValue, final boolean negate) {
		final BigDecimal number = new BigDecimal(cellValue);
		return negate ? number.negate() : number;
	}

	@EqualsAndHashCode.Include
	int year;

	@EqualsAndHashCode.Include
	BudgetType type;

	@ToString.Exclude
	Map<Account, Balance> balances = new TreeMap<>();

	Map<BudgetReference, String> references = new EnumMap<>(BudgetReference.class);

	@NonFinal
	boolean modifiable = true;

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
		return modifiable ? balances : unmodifiableMap(balances);
	}

	public Map<BudgetReference, String> getReferences() {
		return unmodifiableMap(references);
	}

	public int removeEmptyBalances() {
		if (!modifiable) {
			throw new UnsupportedOperationException();
		}

		int count = 0;
		final Iterator<Balance> iterator = getBalances().values().iterator();
		while (iterator.hasNext()) {
			final Balance balance = iterator.next();
			if (balance.getValue().compareTo(BigDecimal.ZERO) == 0) {
				iterator.remove();
				count += 1;
			}
		}
		return count;
	}

	public void setReferenceIfAbsent(final BudgetReference reference, final String value) {
		if (!modifiable) {
			throw new UnsupportedOperationException();
		}
		references.putIfAbsent(reference, value);
	}

	public Budget unmodifiable() {
		modifiable = false;
		return this;
	}
}
