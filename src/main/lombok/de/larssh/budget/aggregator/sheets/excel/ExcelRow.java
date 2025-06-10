package de.larssh.budget.aggregator.sheets.excel;

import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;

import de.larssh.budget.aggregator.utils.CellValues;
import de.larssh.utils.annotations.PackagePrivate;
import lombok.RequiredArgsConstructor;

@PackagePrivate
@RequiredArgsConstructor
class ExcelRow implements de.larssh.budget.aggregator.sheets.Row {
	int firstColumnIndex;

	List<String> header;

	Row row;

	@Override
	public Optional<String> get(final int index) {
		return Optional.ofNullable(row.getCell(firstColumnIndex + index))
				.map(cell -> CellValues.getAsString(CellValues.create(cell, true)));
	}

	@Override
	public Optional<String> get(final String header) {
		final int index = this.header.indexOf(header);
		return index == -1 ? Optional.empty() : get(index);
	}

	@Override
	public int getRowIndex() {
		return row.getRowNum() - firstColumnIndex;
	}

	@Override
	public int size() {
		return row.getLastCellNum() - firstColumnIndex + 1;
	}
}
