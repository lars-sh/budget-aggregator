package de.larssh.budget.aggregator.data;

import java.util.Optional;

import de.larssh.utils.annotations.PackagePrivate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlanType {
	/**
	 * In German: Ergebnisplan
	 */
	RESULT(4_000_000, 6_000_000 - 1),

	/**
	 * In German: Finanzplan
	 */
	INVEST(6_000_000, 8_000_000 - 1);

	@PackagePrivate
	static Optional<PlanType> of(final int accountId) {
		for (final PlanType planType : values()) {
			if (accountId >= planType.getAccountIdMin() && accountId <= planType.getAccountIdMax()) {
				return Optional.of(planType);
			}
		}
		return Optional.empty();
	}

	@Getter(AccessLevel.PRIVATE)
	int accountIdMin;

	@Getter(AccessLevel.PRIVATE)
	int accountIdMax;
}
