package de.larssh.budget.aggregator.cli;

import de.larssh.budget.aggregator.data.BudgetType;
import de.larssh.utils.Nullables;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.RequiredArgsConstructor;
import picocli.CommandLine.ITypeConverter;

@RequiredArgsConstructor
public class BudgetTypeConverter implements ITypeConverter<BudgetType> {
	@Override
	public BudgetType convert(@Nullable final String value) {
		return BudgetType.of(Nullables.orElseThrow(value).trim());
	}
}
