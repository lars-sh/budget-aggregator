package de.larssh.budget.aggregator.cli;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.Set;

import de.larssh.budget.aggregator.data.BudgetType;
import de.larssh.utils.text.SplitLimit;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.RequiredArgsConstructor;
import picocli.CommandLine.ITypeConverter;

@RequiredArgsConstructor
public class BudgetTypesConverter implements ITypeConverter<Set<BudgetType>> {
	@Override
	public Set<BudgetType> convert(@Nullable final String value) {
		return value == null
				? emptySet()
				: Arrays.stream(value.split(",", SplitLimit.NO_LIMIT_AND_STRIP_EMPTY_TRAILING))
						.map(v -> BudgetType.of(v.trim()))
						.collect(toSet());
	}
}
