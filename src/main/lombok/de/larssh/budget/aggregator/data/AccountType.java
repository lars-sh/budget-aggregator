package de.larssh.budget.aggregator.data;

import java.util.Optional;

import de.larssh.utils.annotations.PackagePrivate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountType {
	/**
	 * In German: Erträge im Ergebnisplan/Ergebnisrechnung
	 */
	RESULT_POSITIVE(1, 4_000_000, 5_000_000 - 1),

	/**
	 * In German: Aufwendungen im Ergebnisplan/Ergebnisrechnung
	 */
	RESULT_NEGATIVE(-1, 5_000_000, 6_000_000 - 1),

	/**
	 * In German: Einzahlungen im Finanzplan/Finanzrechnung
	 */
	INVEST_POSITIVE(1, 6_000_000, 7_000_000 - 1),

	/**
	 * In German: Auszahlungen im Finanzplan/Finanzrechnung
	 */
	INVEST_NEGATIVE(-1, 7_000_000, 8_000_000 - 1);

	@PackagePrivate
	@SuppressWarnings("PMD.ShortMethodName")
	static Optional<AccountType> of(final int accountId) {
		for (final AccountType accountType : values()) {
			if (accountId >= accountType.getAccountIdMin() && accountId <= accountType.getAccountIdMax()) {
				return Optional.of(accountType);
			}
		}
		return Optional.empty();
	}

	int sign;

	@Getter(AccessLevel.PRIVATE)
	int accountIdMin;

	@Getter(AccessLevel.PRIVATE)
	int accountIdMax;
}
