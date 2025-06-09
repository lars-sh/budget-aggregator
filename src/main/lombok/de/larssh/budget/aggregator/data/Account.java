package de.larssh.budget.aggregator.data;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

import de.larssh.budget.aggregator.file.CsvFiles;
import de.larssh.budget.aggregator.utils.Comparators;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.text.CsvRow;
import de.larssh.utils.text.Patterns;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * In German: Konto/Position/Haushaltsstelle
 */
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Account implements Comparable<Account> {
	private static final Pattern CELL_PATTERN = Pattern.compile("^\\s*(?<id>\\d+)\\s*(?<description>.*?)\\s*$");

	private static final Comparator<Account> COMPARATOR = Comparator.comparing(Account::getProduct)
			.thenComparingInt(Account::getId)
			.thenComparing(Comparators.compareCaseInsensitiveFirst(Account::getDescription));

	@PackagePrivate
	@SuppressWarnings({ "PMD.LooseCoupling", "PMD.ShortMethodName" })
	static Optional<Account> of(final CsvRow row) {
		final Optional<Product> product = Product.of(row);
		if (!product.isPresent()) {
			return Optional.empty();
		}

		final Optional<String> accountCell = row.get(CsvFiles.HEADER_ACCOUNT);
		if (!accountCell.isPresent()) {
			return Optional.empty();
		}

		return Patterns.matches(CELL_PATTERN, accountCell.get())
				.map(matcher -> new Account(product.get(),
						Integer.parseInt(matcher.group("id")),
						matcher.group("description")));
	}

	@EqualsAndHashCode.Include
	Product product;

	@EqualsAndHashCode.Include
	@SuppressWarnings("PMD.ShortVariable")
	int id;

	String description;

	@Override
	public int compareTo(@Nullable final Account other) {
		return COMPARATOR.compare(this, other);
	}

	public AccountType getType() {
		return AccountType.of(getId())
				.orElseThrow(() -> new IllegalArgumentException(
						String.format("Failed determining the account type for account ID %d.", getId())));
	}
}
