package de.larssh.budget.aggregator.sheets;

import java.util.Optional;

public interface SheetsFile {
	Optional<String> getFileName();

	@SuppressWarnings("java:S1452")
	Iterable<? extends Sheet> getSheets();
}
