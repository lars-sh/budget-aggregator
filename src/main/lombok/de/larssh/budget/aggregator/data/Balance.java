package de.larssh.budget.aggregator.data;

import java.math.BigDecimal;
import java.util.Comparator;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Balance implements Comparable<Balance> {
	private static final Comparator<Balance> COMPARATOR
			= Comparator.comparing(Balance::getAccount).thenComparing(Balance::getValue);

	@EqualsAndHashCode.Include
	Account account;

	@EqualsAndHashCode.Include
	BigDecimal value;

	@Override
	public int compareTo(@Nullable final Balance other) {
		return COMPARATOR.compare(this, other);
	}
}
