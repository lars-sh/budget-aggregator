package de.larssh.budget.aggregator.sheets.excel;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import de.larssh.budget.aggregator.utils.CellValues;
import de.larssh.budget.aggregator.utils.Workbooks;
import de.larssh.utils.annotations.PackagePrivate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@PackagePrivate
class ExcelSheet implements de.larssh.budget.aggregator.sheets.Sheet {
	@Getter(AccessLevel.PRIVATE)
	Sheet sheet;

	@Getter
	List<String> header;

	@Getter
	Iterable<ExcelRow> rows;

	public ExcelSheet(final Sheet sheet) {
		this.sheet = sheet;

		final int firstColumnIndex = Workbooks.rows(sheet) //
				.mapToInt(Row::getFirstCellNum)
				.min()
				.orElse(0);

		// Header
		final int firstRowIndex = sheet.getFirstRowNum();
		header = Workbooks.cells(sheet.getRow(firstRowIndex)) //
				.map(cell -> CellValues.getAsString(CellValues.create(cell, true)))
				.collect(toList());

		// Rows
		rows = Workbooks.rows(sheet) //
				.skip(1) // skip header
				.map(row -> new ExcelRow(firstColumnIndex, header, row))
				.collect(toList());
	}

	@Override
	public Optional<String> getName() {
		return Optional.of(sheet.getSheetName());
	}

	@Override
	public int size() {
		return sheet.getLastRowNum() - sheet.getFirstRowNum() + 1;
	}
}
