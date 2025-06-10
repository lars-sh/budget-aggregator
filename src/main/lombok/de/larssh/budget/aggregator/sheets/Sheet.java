package de.larssh.budget.aggregator.sheets;

import java.util.List;
import java.util.Optional;

public interface Sheet {
	List<String> getHeader();

	Optional<String> getName();

	@SuppressWarnings("java:S1452")
	Iterable<? extends Row> getRows();

	int size();
}
