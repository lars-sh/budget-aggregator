package de.larssh.budget.aggregator.sheets.csv;

import java.util.Optional;

import de.larssh.budget.aggregator.sheets.Row;
import de.larssh.utils.annotations.PackagePrivate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@PackagePrivate
class CsvRow implements Row {
	@SuppressWarnings("PMD.LooseCoupling")
	de.larssh.utils.text.CsvRow row;

	@Override
	public Optional<String> get(final int index) {
		return index < row.size() ? Optional.of(row.get(index)) : Optional.empty();
	}

	@Override
	public Optional<String> get(final String header) {
		return row.get(header);
	}

	@Override
	public int getRowIndex() {
		return row.getRowIndex();
	}

	@Override
	public int size() {
		return row.size();
	}
}
