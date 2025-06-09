package de.larssh.budget.aggregator.data;

import java.util.Comparator;
import java.util.Optional;

import de.larssh.budget.aggregator.file.CsvFiles;
import de.larssh.budget.aggregator.utils.Comparators;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.text.CsvRow;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * In German: Produkt/Budget
 */
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Product implements Comparable<Product> {
	private static final Comparator<Product> COMPARATOR = Comparator.comparing(Product::getMunicipality)
			.thenComparingInt(Product::getId)
			.thenComparing(Comparators.compareCaseInsensitiveFirst(Product::getDescription));

	@PackagePrivate
	@SuppressWarnings({ "PMD.LooseCoupling", "PMD.ShortMethodName", "PMD.ShortVariable" })
	static Optional<Product> of(final CsvRow row) {
		final Optional<Municipality> municipality = Municipality.of(row);
		if (!municipality.isPresent()) {
			return Optional.empty();
		}

		final Optional<String> id = row.get(CsvFiles.HEADER_PRODUCT_ID);
		if (!id.isPresent() || Strings.isBlank(id.get())) {
			return Optional.empty();
		}

		final Optional<String> description = row.get(CsvFiles.HEADER_PRODUCT_DESCRIPTION);
		if (!id.isPresent()) {
			return Optional.empty();
		}

		return Optional.of(new Product(municipality.get(), Integer.parseInt(id.get()), description.get().trim()));
	}

	@EqualsAndHashCode.Include
	Municipality municipality;

	@EqualsAndHashCode.Include
	@SuppressWarnings("PMD.ShortVariable")
	int id;

	String description;

	@Override
	public int compareTo(@Nullable final Product other) {
		return COMPARATOR.compare(this, other);
	}
}
