package de.larssh.budget.aggregator.sheets.csv;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import de.larssh.budget.aggregator.sheets.Sheet;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.text.Csv;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@PackagePrivate
class CsvSheet implements Sheet {
	@Getter(AccessLevel.PRIVATE)
	Csv csv;

	@Getter
	Collection<CsvRow> rows;

	@PackagePrivate
	CsvSheet(final Csv csv) {
		this.csv = csv;

		rows = csv.stream()
				.skip(1) // Skip header
				.map(CsvRow::new)
				.collect(toList());
	}

	@Override
	public List<String> getHeader() {
		return csv.getHeaders();
	}

	@Override
	public Optional<String> getName() {
		return Optional.empty();
	}

	@Override
	public int size() {
		return rows.size();
	}
}
