package de.larssh.budget.aggregator.cli;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.larssh.utils.text.Patterns;
import de.larssh.utils.text.SplitLimit;
import edu.umd.cs.findbugs.annotations.Nullable;
import picocli.CommandLine.ITypeConverter;

public class YearsConverter implements ITypeConverter<Set<Integer>> {
	private static final Pattern YEARS_PATTERN = Pattern.compile("^\\s*(?<from>\\d+)\\s*(-(?<to>\\d+))?\\s*$");

	@Override
	public Set<Integer> convert(@Nullable final String value) {
		return value == null
				? emptySet()
				: Arrays.stream(value.split(",", SplitLimit.NO_LIMIT_AND_STRIP_EMPTY_TRAILING))
						.flatMap(v -> convertSinglePattern(v).stream())
						.collect(toSet());
	}

	private Set<Integer> convertSinglePattern(final String value) {
		final Matcher matcher = Patterns.matches(YEARS_PATTERN, value)
				.orElseThrow(
						() -> new IllegalArgumentException(String.format("Unexpected year pattern \"%s\".", value)));

		final int from = Integer.parseInt(matcher.group("from"));
		if (matcher.group("to") == null) {
			return singleton(from);
		}

		final Set<Integer> years = new HashSet<>();
		final int to = Integer.parseInt(matcher.group("to"));
		for (int year = from; year <= to; year += 1) {
			years.add(year);
		}
		return years;
	}
}
