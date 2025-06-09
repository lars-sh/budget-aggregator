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

import de.larssh.budget.aggregator.file.CsvFiles;
import de.larssh.utils.Nullables;
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

	@SuppressWarnings({
			"checkstyle:XIllegalCatchDefault",
			"PMD.AvoidCatchingGenericException",
			"PMD.LooseCoupling",
			"PMD.ShortMethodName" })
	public static Set<Budget> of(final Csv csv) throws StringParseException {
		final int lastNonBalanceColumn = csv.getHeaders().indexOf(CsvFiles.HEADER_ACCOUNT);
		if (lastNonBalanceColumn == -1) {
			return emptySet();
		}

		final Map<Budget, Budget> budgets = new LinkedHashMap<>();
		for (final CsvRow row : csv.subList(1, csv.size())) {
			try {
				final Optional<Account> account = Account.of(row);
				if (account.isPresent()) {
					final int headerSize = csv.getHeaders().size();
					for (int columnIndex = lastNonBalanceColumn + 1; columnIndex < headerSize; columnIndex += 1) {
						addBalance(budgets, account.get(), row, columnIndex);
					}
				}
			} catch (final Exception e) {
				throw new StringParseException(e, "Failed reading row %d.", row.getRowIndex());
			}
		}
		return budgets.keySet();
	}

	@SuppressWarnings("PMD.LooseCoupling")
	private static void addBalance(final Map<Budget, Budget> budgets,
			final Account account,
			final CsvRow row,
			final int columnIndex) {
		if (columnIndex >= row.size()) {
			return;
		}
		final String cellValue = Nullables.orElseThrow(row.get(columnIndex));
		if (Strings.isBlank(cellValue)) {
			return;
		}

		final String title = row.getCsv().getHeaders().get(columnIndex);
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
		row.get(CsvFiles.HEADER_BUDGET_YEAR).ifPresent(y -> budget.setReference(BudgetReference.BUDGET_YEAR, y));
		budget.setReference(BudgetReference.COLUMN, CellReference.convertNumToColString(columnIndex));

		final Balance balance = new Balance(account, determineValue(cellValue, account));
		budgets.computeIfAbsent(budget, Function.identity()).balances.put(account, balance);
	}

	@SuppressWarnings("PMD.LooseCoupling")
	private static OptionalInt determineYear(final CsvRow row, final Matcher columnHeaderMatcher) {
		final String year = columnHeaderMatcher.group("year");
		if (year != null) {
			return OptionalInt.of(Integer.parseInt(year));
		}

		final Optional<String> yearCell = row.get(CsvFiles.HEADER_BUDGET_YEAR);
		if (!yearCell.isPresent() || Strings.isBlank(yearCell.get())) {
			return OptionalInt.empty();
		}

		final int offsetYears = columnHeaderMatcher.group("yearBefore") == null ? 0 : -1;
		return OptionalInt.of(Integer.parseInt(yearCell.get()) + offsetYears);
	}

	private static BigDecimal determineValue(final String cellValue, final Account account) {
		final BigDecimal number = new BigDecimal(cellValue);
		return account.getType().getSign() < 0 ? number.negate() : number;
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

	public void setReference(final BudgetReference reference, final String value) {
		if (!modifiable) {
			throw new UnsupportedOperationException();
		}
		references.put(reference, value);
	}

	public Budget unmodifiable() {
		modifiable = false;
		return this;
	}
}
