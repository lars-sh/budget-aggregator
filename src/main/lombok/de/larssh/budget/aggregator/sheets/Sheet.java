package de.larssh.budget.aggregator.sheets;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.larssh.budget.aggregator.data.BudgetReference;

public interface Sheet {
	List<String> getHeader();

	List<Map<BudgetReference, String>> getHeaderReferences();

	Optional<String> getName();

	@SuppressWarnings("java:S1452")
	Iterable<? extends Row> getRows();

	boolean isApplyBudgetTypeSign();

	int size();
}
