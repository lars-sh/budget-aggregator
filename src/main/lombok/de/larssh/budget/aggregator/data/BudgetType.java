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
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class BudgetType implements Comparable<BudgetType> {
	private static final Map<String, BudgetType> CACHE = synchronizedMap(new HashMap<>());

	private static final Set<BudgetType> DEFAULT_VALUES = unmodifiableSet(new LinkedHashSet<>(Arrays.asList( //
			of("Plan"),
			of("Ist"),
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

	public static BudgetType of(final String name) {
		if (Strings.isBlank(name)) {
			throw new IllegalArgumentException("The budget type name must not be blank.");
		}
		if (name.equals("Ergebnis")) {
			return of("Ist");
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
