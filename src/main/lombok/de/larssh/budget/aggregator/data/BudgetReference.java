package de.larssh.budget.aggregator.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BudgetReference {
	FILE_NAME("Datei"),
	SHEET("Registerkarte"),
	COLUMN("Spalte"),
	BUDGET_YEAR("Haushaltsjahr");

	String displayValue;
}
