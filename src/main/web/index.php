<?php
define('DEBUG', true); // TODO: false

ini_set('html_errors', false);
if (DEBUG === true) {
	error_reporting(-1);
	ini_set('display_errors', true);
} else {
	error_reporting(0);
	ini_set('display_errors', false);
}

set_error_handler(function ($errno, $errstr, $errfile = null, $errline = null) {
	throw new ErrorException($errstr, 0, $errno, $errfile, $errline);
});

// Initialize Parameters
$isPostRequest = $_SERVER['REQUEST_METHOD'] === 'POST';
$filterBudgetTypes = isset($_REQUEST['filter-budget-types']) && is_string($_REQUEST['filter-budget-types']) ? $_REQUEST['filter-budget-types'] : '';
$filterYears = isset($_REQUEST['filter-years']) && is_string($_REQUEST['filter-years']) ? $_REQUEST['filter-years'] : '';
if ($isPostRequest) {
	$hideDuplicateBudgets = isset($_REQUEST['hide-duplicate-budgets']) && $_REQUEST['hide-duplicate-budgets'] === 'on';
	$hideEmptyAccounts = isset($_REQUEST['hide-empty-accounts']) && $_REQUEST['hide-empty-accounts'] === 'on';
	$hideEmptyBalances = isset($_REQUEST['hide-empty-balances']) && $_REQUEST['hide-empty-balances'] === 'on';
	$hideEmptyBudgets = isset($_REQUEST['hide-empty-budgets']) && $_REQUEST['hide-empty-budgets'] === 'on';
} else {
	$hideDuplicateBudgets = !isset($_REQUEST['hide-duplicate-budgets']) || $_REQUEST['hide-duplicate-budgets'] !== 'false';
	$hideEmptyAccounts = !isset($_REQUEST['hide-empty-accounts']) || $_REQUEST['hide-empty-accounts'] !== 'false';
	$hideEmptyBalances = !isset($_REQUEST['hide-empty-balances']) || $_REQUEST['hide-empty-balances'] !== 'false';
	$hideEmptyBudgets = !isset($_REQUEST['hide-empty-budgets']) || $_REQUEST['hide-empty-budgets'] !== 'false';
}
$format = isset($_REQUEST['format']) && in_array($_REQUEST['format'], array('csv', 'xlsx'), true) ? $_REQUEST['format'] : 'xlsx';

// Call Budget Aggregator
$message = null;
if ($isPostRequest) {
	$sources = array();
	if (isset($_FILES['sources']) && is_array($_FILES['sources']['tmp_name'])) {
		foreach ($_FILES['sources']['tmp_name'] as $file) {
			if (is_uploaded_file($file)) {
				$sources[] = $file;
			}
		}
	}

	if (count($sources) === 0) {
		http_response_code(400);
		$message = 'Mindestens eine Datei muss hochgeladen werden.';
	} else {
		$exit = false;
		try {
			$output = null;
			if ($format === 'xlsx') {
				$output = tempnam('temp', 'budget-aggregator-');
			}

			$result = callBudgetAggregator($filterBudgetTypes, $filterYears, $hideDuplicateBudgets, $hideEmptyAccounts, $hideEmptyBalances, $hideEmptyBudgets, $output, $sources);

			http_response_code(200);
			header('Content-Disposition: attachment; filename="budget-aggregator.' . $format . '"');
			header('Content-Type: ' . ($format === 'xlsx' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'text/csv; charset=UTF-8'));
			echo $format === 'xlsx' ? file_get_contents($output) : $result;
			$exit = true;
		} catch (Exception $e) {
			http_response_code(500);
			if (DEBUG === true) {
				header('Content-Type: text/plain; charset=UTF-8');
				echo $e;
				$exit = true;
			} else {
				$message = 'Das Erzeugen des Dokuments ist unerwartet fehlgeschlagen.';
			}
		} finally {
			@unlink($output);
		}
		if ($exit) {
			exit;
		}
	}
}

function callBudgetAggregator($filterBudgetTypes, $filterYears, $hideDuplicateBudgets, $hideEmptyAccounts, $hideEmptyBalances, $hideEmptyBudgets, $output, $sources) {
	$command = '/usr/bin/java -jar';
	$command .= ' ' . escapeshellarg(realpath('libraries/budget-aggregator-0.9.0-SNAPSHOT.jar'));
	$command .= $filterBudgetTypes === '' ? '' : ' ' . escapeshellarg('--filter-budget-types=' . $filterBudgetTypes);
	$command .= $filterYears === '' ? '' : ' ' . escapeshellarg('--filter-years=' . $filterYears);
	$command .= ' ' . escapeshellarg($hideDuplicateBudgets ? '--hide-duplicate-budgets' : '--no-hide-duplicate-budgets');
	$command .= ' ' . escapeshellarg($hideEmptyAccounts ? '--hide-empty-accounts' : '--no-hide-empty-accounts');
	$command .= ' ' . escapeshellarg($hideEmptyBalances ? '--hide-empty-balances' : '--no-hide-empty-balances');
	$command .= ' ' . escapeshellarg($hideEmptyBudgets ? '--hide-empty-budgets' : '--no-hide-empty-budgets');
	if (is_string($output)) {
		$command .= ' ' . escapeshellarg('--output=' . $output);
	}
	foreach ($sources as $source) {
		$command .= ' ' . escapeshellarg(realpath($source));
	}

	exec($command, $result, $result_code);
	if ($result_code !== 0) {
		throw new Exception(sprintf('Unexpected result code %d when executing: %s', $result_code, $command));
	}
	return implode("\n", $result) . "\n";
}

// Content Type
$type = 'application/xhtml+xml';
if (stripos($_SERVER['HTTP_ACCEPT'], $type) === false) {
	$type = 'text/html';
}

// Header
header('Content-Type: ' . $type . '; charset=UTF-8');
echo '<?xml version="1.0" encoding="UTF-8" ?>';
?>
<!DOCTYPE html>
<html lang="de" xmlns="http://www.w3.org/1999/xhtml" xml:lang="de">
	<head>
		<link href="https://t30.knickrehm.net/budget-aggregator/" rel="canonical" />
		<link href="get/style.css" rel="stylesheet" type="text/css" />
		<title>Budget Aggregator</title>
	</head>
	<body>
		<div class="content">
			<h1>Budget Aggregator</h1>
			<p class="lead">Der Budget Aggregator liest Excel-Exporte der Software „CIP kommunal“ ein und bereitet sie in echter Tabellenform auf, um das Erstellen von Auswertungen zu erleichtern.</p>
			<p>Dabei kann sie mehrere Exporte zusammenfassen und somit Übersichten über viele Jahre erzeugen. Zugleich erstellt sie eine separate Tabelle als Übersicht der Produkte.</p>
			<p>Unterstützt werden Ergebnispläne und -rechnungen genauso wie Teilfinanzpläne und -rechnungen. Unerwartete Registerkarten werden ignoriert.</p>

			<h3>Dokumente aufbereiten</h3>
			<?php echo $message === null ? '' : '<p class="warning">' . htmlspecialchars($message, ENT_QUOTES, 'UTF-8') .'</p>'; ?>

			<form>
				<p>
					<label for="sources">Dateien hochladen</label>
					<input accept=".csv,.xls,.xlsx" multiple="true" name="sources[]" id="sources" type="file" />
				</p>
				<p>
					<label for="filter-budget-types">Nach Haushaltstypen filtern</label>
					<input name="filter-budget-types" id="filter-budget-types" placeholder="Beispiel: &quot;Plan, Ist&quot;" type="text" value="<?php echo htmlspecialchars($filterBudgetTypes, ENT_QUOTES, 'UTF-8'); ?>" />
				</p>
				<p>
					<label for="filter-years">Nach Jahren filtern</label>
					<input name="filter-years" id="filter-years" placeholder="Beispiel: &quot;<?php $year = date('Y'); echo strval(intval($year) - 1) . ', ' . $year; ?>&quot;" type="text" value="<?php echo htmlspecialchars($filterYears, ENT_QUOTES, 'UTF-8'); ?>" />
				</p>
				<p>
					<div></div>
					<div><input <?php echo $hideDuplicateBudgets ? 'checked="true" ' : ''; ?>name="hide-duplicate-budgets" id="hide-duplicate-budgets" type="checkbox" /><label for="hide-duplicate-budgets">Doppelte Haushalte entfernen</label></div>
				</p>
				<p>
					<div></div>
					<div><input <?php echo $hideEmptyAccounts ? 'checked="true" ' : ''; ?>name="hide-empty-accounts" id="hide-empty-accounts" type="checkbox" /><label for="hide-empty-accounts">Ungenutzte Konten entfernen</label></div>
				</p>
				<p>
					<div></div>
					<div><input <?php echo $hideEmptyBalances ? 'checked="true" ' : ''; ?>name="hide-empty-balances" id="hide-empty-balances" type="checkbox" /><label for="hide-empty-balances">Ungenutzte Produkte entfernen</label></div>
				</p>
				<p>
					<div></div>
					<div><input <?php echo $hideEmptyBudgets ? 'checked="true" ' : ''; ?>name="hide-empty-budgets" id="hide-empty-budgets" type="checkbox" /><label for="hide-empty-budgets">Ungenutzte Haushalte entfernen</label></div>
				</p>
				<p>
					<label for="format">Format</label>
					<select name="format" id="format">
						<option <?php echo $format === 'csv' ? 'selected="true" ' : ''; ?>value="csv">CSV</option>
						<option <?php echo $format === 'xlsx' ? 'selected="true" ' : ''; ?>value="xlsx">Excel-Dokument (XLSX)</option>
					</select>
				</p>
				<p>
					<div></div>
					<input formaction="" formenctype="multipart/form-data" formmethod="post" type="submit" value="Aufbereiten und herunterladen" />
				</p>
			</form>
		</div>
		<div class="footer">
			<span style="float: right;"><a href="impressum.html">Impressum</a></span>
			© <a href="http://lars-sh.de/" target="_blank">Lars Knickrehm</a>
		</div>
	</body>
</html>
