package de.larssh.budget.aggregator.data;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.larssh.budget.aggregator.sheets.Sheet;
import de.larssh.budget.aggregator.sheets.SheetsFile;
import de.larssh.utils.text.StringParseException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Budgets {
	public static Set<Account> getAccounts(final Collection<Budget> budgets) {
		return budgets.stream()
				.flatMap(budget -> budget.getBalances().keySet().stream())
				.collect(toCollection(TreeSet::new));
	}

	public static Set<Product> getProducts(final Collection<Budget> budgets) {
		return budgets.stream()
				.flatMap(budget -> budget.getBalances().keySet().stream())
				.map(Account::getProduct)
				.collect(toCollection(TreeSet::new));
	}

	@SuppressWarnings("PMD.ShortMethodName")
	public static List<Budget> of(final SheetsFile sheetsFile) throws StringParseException {
		final List<Budget> budgets = new ArrayList<>();
		for (final Sheet sheet : sheetsFile.getSheets()) {
			final Set<Budget> budget = Budget.of(sheet);
			budgets.addAll(budget);

			// Add References
			sheetsFile.getFileName()
					.ifPresent(fileName -> setReferenceIfAbsent(budget, BudgetReference.FILE_NAME, fileName));
			sheet.getName().ifPresent(sheetName -> setReferenceIfAbsent(budget, BudgetReference.SHEET, sheetName));
		}
		return budgets;
	}

	public static void removeDuplicateBudgets(final List<Budget> budgets) {
		// Prerequisite: budgets must be sorted!
		for (int index = budgets.size() - 1; index > 0; index -= 1) {
			if (budgets.get(index).equalsIncludingBalances(budgets.get(index - 1))) {
				budgets.remove(index);
				index -= 1;
			}
		}
	}

	public static void removeEmptyAccounts(final Collection<Budget> budgets) {
		// Get all accounts
		final Set<Account> accounts = budgets.stream() //
				.flatMap(budget -> budget.getBalances().keySet().stream())
				.collect(toSet());

		for (final Account account : accounts) {
			// Check if current account is empty
			final boolean isEmptyAccount = budgets.stream()
					.map(budget -> budget.getBalances().get(account))
					.allMatch(balance -> balance == null || balance.getValue().compareTo(BigDecimal.ZERO) == 0);

			// Remove account if empty
			if (isEmptyAccount) {
				for (final Budget budget : budgets) {
					budget.getBalances().remove(account);
				}
			}
		}
	}

	public static void removeEmptyBudgets(final Collection<Budget> budgets) {
		budgets.removeIf(budget -> budget.getBalances()
				.values()
				.stream()
				.allMatch(balance -> balance.getValue().compareTo(BigDecimal.ZERO) == 0));
	}

	private static void setReferenceIfAbsent(final Set<Budget> budgets,
			final BudgetReference reference,
			final String value) {
		for (final Budget budget : budgets) {
			budget.setReferenceIfAbsent(reference, value);
		}
	}
}
