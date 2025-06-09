package de.larssh.budget.aggregator.data;

import static java.util.Collections.synchronizedMap;
import static java.util.Collections.unmodifiableSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.larssh.budget.aggregator.utils.Comparators;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BudgetType implements Comparable<BudgetType> {
	private static final Map<String, BudgetType> CACHE = synchronizedMap(new HashMap<>());

	private static final BudgetType IST = of("Ist");

	private static final String NAME_ERGEBNIS = "Ergebnis";

	private static final Set<BudgetType> DEFAULT_VALUES = unmodifiableSet(new LinkedHashSet<>(Arrays.asList(//
			of("Plan"),
			IST,
			of("Übertragen aus VJ"))));

	private static final Comparator<BudgetType> COMPARATOR_DEFAULT_VALUES = (a, b) -> {
		for (final BudgetType budgetType : DEFAULT_VALUES) {
			if (budgetType.equals(a)) {
				return budgetType.equals(b) ? 0 : -1;
			}
			if (budgetType.equals(b)) {
				return 1;
			}
		}
		return 0;
	};

	private static final Comparator<BudgetType> COMPARATOR = COMPARATOR_DEFAULT_VALUES //
			.thenComparing(Comparators.compareCaseInsensitiveFirst(BudgetType::getName));

	@SuppressWarnings("PMD.ShortMethodName")
	@SuppressFBWarnings(value = "WEM_WEAK_EXCEPTION_MESSAGING",
			justification = "no relevant information available here")
	public static BudgetType of(final String name) {
		if (Strings.isBlank(name)) {
			throw new IllegalArgumentException("The budget type name must not be blank.");
		}
		if (NAME_ERGEBNIS.equals(name)) {
			return IST;
		}
		return CACHE.computeIfAbsent(name, BudgetType::new);
	}

	@EqualsAndHashCode.Include
	String name;

	@Override
	public int compareTo(@Nullable final BudgetType other) {
		return COMPARATOR.compare(this, other);
	}
}
