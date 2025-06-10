package de.larssh.budget.aggregator.sheets.excel;

import static java.util.stream.Collectors.toList;

import java.util.Optional;

import org.apache.poi.ss.usermodel.Workbook;

import de.larssh.budget.aggregator.sheets.SheetsFile;
import de.larssh.budget.aggregator.utils.Workbooks;
import lombok.Getter;

public class ExcelSheets implements SheetsFile {
	@Getter
	Optional<String> fileName;

	@Getter
	Iterable<ExcelSheet> sheets;

	public ExcelSheets(final String fileName, final Workbook workbook) {
		this.fileName = Optional.of(fileName);

		sheets = Workbooks.sheets(workbook) //
				.map(ExcelSheet::new)
				.collect(toList());
	}
}
