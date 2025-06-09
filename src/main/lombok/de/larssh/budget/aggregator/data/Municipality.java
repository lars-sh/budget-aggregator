package de.larssh.budget.aggregator.data;

import static java.util.Collections.synchronizedMap;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import de.larssh.budget.aggregator.file.CsvFiles;
import de.larssh.utils.annotations.PackagePrivate;
import de.larssh.utils.text.CsvRow;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * In German: Kommune/Gemeinde/GKZ
 */
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Municipality implements Comparable<Municipality> {
	private static final Map<Integer, Municipality> CACHE = synchronizedMap(new HashMap<>());

	private static final Comparator<Municipality> COMPARATOR = Comparator.comparingInt(Municipality::getId);

	@PackagePrivate
	@SuppressFBWarnings(value = "NAB_NEEDLESS_BOXING_VALUEOF",
			justification = "false-positive, not boxing an int explicitly here")
	@SuppressWarnings({ "PMD.LooseCoupling", "PMD.ShortMethodName", "PMD.ShortVariable" })
	static Optional<Municipality> of(final CsvRow row) {
		final Optional<String> id = row.get(CsvFiles.HEADER_MUNICIPALITY);
		if (!id.isPresent() || Strings.isBlank(id.get())) {
			return Optional.empty();
		}

		return Optional.of(CACHE.computeIfAbsent(Integer.parseInt(id.get()), Municipality::new));
	}

	@EqualsAndHashCode.Include
	@SuppressWarnings("PMD.ShortVariable")
	int id;

	@Override
	public int compareTo(@Nullable final Municipality other) {
		return COMPARATOR.compare(this, other);
	}
}
