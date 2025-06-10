package de.larssh.budget.aggregator.sheets.excel;

import static de.larssh.utils.Collectors.toMap;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import de.larssh.budget.aggregator.data.BudgetReference;
import de.larssh.budget.aggregator.utils.CellValues;
import de.larssh.budget.aggregator.utils.Workbooks;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.collection.Maps;
import de.larssh.utils.text.Patterns;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@PackagePrivate
class ExcelSheet implements de.larssh.budget.aggregator.sheets.Sheet {
	private static final Map<BudgetReference, Pattern> HEADER_PATTERNS = Arrays.stream(BudgetReference.values())
			.map(reference -> Maps.entry(reference,
					Pattern.compile('^' + Pattern.quote(reference.getDisplayValue()) + ": (?<value>.*)$",
							Pattern.MULTILINE)))
			.collect(toMap(HashMap::new));

	private static Map<BudgetReference, String> parseHeaderComment(final Cell cell) {
		final String comment = Optional.ofNullable(cell)
				.map(Cell::getCellComment)
				.map(Comment::getString)
				.map(RichTextString::getString)
				.orElse(null);
		if (comment == null) {
			return emptyMap();
		}

		final Map<BudgetReference, String> headerReferences = new EnumMap<>(BudgetReference.class);
		for (final Entry<BudgetReference, Pattern> entry : HEADER_PATTERNS.entrySet()) {
			Patterns.find(entry.getValue(), comment)
					.ifPresent(matcher -> headerReferences.put(entry.getKey(), matcher.group("value")));
		}
		return unmodifiableMap(headerReferences);
	}

	@Getter(AccessLevel.PRIVATE)
	Sheet sheet;

	@Getter
	List<String> header;

	@Getter
	List<Map<BudgetReference, String>> headerReferences;

	@Getter
	Iterable<ExcelRow> rows;

	@PackagePrivate
	ExcelSheet(final Sheet sheet) {
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

		// Header References
		headerReferences = Workbooks.cells(sheet.getRow(firstRowIndex)) //
				.map(ExcelSheet::parseHeaderComment)
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
	public boolean isApplyBudgetTypeSign() {
		return !getHeader().contains(ExcelFiles.COLUMN_NAME_MUNICIPALITY);
	}

	@Override
	public int size() {
		return sheet.getLastRowNum() - sheet.getFirstRowNum() + 1;
	}
}
