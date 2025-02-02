package de.larssh.budget.aggregator.utils;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Comparators {
	public static <T> Comparator<T> compareCaseInsensitiveFirst(final Function<T, String> keyExtractor) {
		return ((Comparator<T>) (first, second) -> String.CASE_INSENSITIVE_ORDER.compare(keyExtractor.apply(first),
				keyExtractor.apply(second))).thenComparing(keyExtractor);
	}

	public static <T, V extends Comparable<V>> Comparator<T> compareOptional(
			final Function<T, Optional<V>> keyExtractor) {
		return (first, second) -> {
			final Optional<V> firstOptional = keyExtractor.apply(first);
			final Optional<V> secondOptional = keyExtractor.apply(second);

			if (firstOptional.isPresent()) {
				return secondOptional.isPresent() ? firstOptional.get().compareTo(secondOptional.get()) : 1;
			}
			return secondOptional.isPresent() ? -1 : 0;
		};
	}
}
