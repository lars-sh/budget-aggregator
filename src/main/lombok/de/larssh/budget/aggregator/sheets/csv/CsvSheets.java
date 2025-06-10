package de.larssh.budget.aggregator.sheets.csv;

import static java.util.Collections.singleton;

import java.util.Optional;

import de.larssh.budget.aggregator.sheets.SheetsFile;
import de.larssh.utils.text.Csv;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CsvSheets implements SheetsFile {
	@Getter
	Optional<String> fileName;

	@Getter
	Iterable<CsvSheet> sheets;

	@SuppressWarnings("PMD.LooseCoupling")
	public CsvSheets(final String fileName, final Csv csv) {
		this.fileName = Optional.of(fileName);
		sheets = singleton(new CsvSheet(csv));
	}
}
