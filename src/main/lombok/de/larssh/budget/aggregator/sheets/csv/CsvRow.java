package de.larssh.budget.aggregator.sheets.csv;

import java.util.Optional;

import de.larssh.budget.aggregator.sheets.Row;
import de.larssh.utils.annotations.PackagePrivate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@PackagePrivate
class CsvRow implements Row {
	de.larssh.utils.text.CsvRow csvRow;

	@Override
	public Optional<String> get(final int index) {
		return index < csvRow.size() ? Optional.of(csvRow.get(index)) : Optional.empty();
	}

	@Override
	public Optional<String> get(final String header) {
		return csvRow.get(header);
	}

	@Override
	public int getRowIndex() {
		return csvRow.getRowIndex();
	}

	@Override
	public int size() {
		return csvRow.size();
	}
}
