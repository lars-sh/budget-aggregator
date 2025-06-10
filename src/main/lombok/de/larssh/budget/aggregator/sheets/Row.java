package de.larssh.budget.aggregator.sheets;

import java.util.Optional;

@SuppressWarnings("PMD.ShortClassName")
public interface Row {
	Optional<String> get(int index);

	Optional<String> get(String header);

	int getRowIndex();

	int size();
}
