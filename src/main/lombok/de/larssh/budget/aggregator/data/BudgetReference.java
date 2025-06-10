package de.larssh.budget.aggregator.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BudgetReference {
	BUDGET_YEAR("Haushaltsjahr"),
	FILE_NAME("Datei"),
	SHEET("Registerkarte"),
	COLUMN("Spalte");

	@Getter
	String displayValue;
}
