/*
 * Copyright 2015-2019 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.params.provider;

import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.univocity.parsers.common.processor.ObjectRowListProcessor;
import com.univocity.parsers.conversions.Conversion;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.platform.commons.util.BlacklistedExceptions;
import org.junit.platform.commons.util.PreconditionViolationException;
import org.junit.platform.commons.util.Preconditions;

import static com.univocity.parsers.conversions.Conversions.toNull;
import static com.univocity.parsers.conversions.Conversions.trim;

/**
 * @since 5.0
 */
class CsvArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<CsvSource> {

	private static final String LINE_SEPARATOR = "\n";

	private CsvSource annotation;

	@Override
	public void accept(CsvSource annotation) {
		this.annotation = annotation;
	}

	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
		CsvParserSettings settings = new CsvParserSettings();
		settings.getFormat().setDelimiter(this.annotation.delimiter());
		settings.getFormat().setLineSeparator(LINE_SEPARATOR);
		settings.getFormat().setQuote('\'');
		settings.getFormat().setQuoteEscape('\'');
		settings.setEmptyValue(this.annotation.emptyValue());
		settings.setAutoConfigurationEnabled(false);
		AtomicLong index = new AtomicLong(0);

		// @formatter:off
		return Arrays.stream(this.annotation.value())
				.map(line -> {
				String[] parsedLine = null;
				try {
					parsedLine = parseLine(line + LINE_SEPARATOR, this.annotation.nullSymbols(), settings);
				}
				catch (Throwable throwable) {
					handleCsvException(throwable, this.annotation);
				}
				Preconditions.notNull(parsedLine,
								   () -> "Line at index " + index.get() + " contains invalid CSV: \"" + line + "\"");
				return parsedLine;
				})
				.peek(values -> index.incrementAndGet())
				.map(Arguments::of);
		// @formatter:on

	}

	static String[] parseLine(String input, String nullValue, CsvParserSettings settings) {
		final List<Object[]> processorRows = parseCsv(input, nullValue, settings);
		if (processorRows.isEmpty()) {
			return null;
		} else {
			return Arrays.stream(processorRows.get(0))
						 .map(o -> (String) o).toArray(String[]::new);
		}
	}

	private static List<Object[]> parseCsv(String input, String nullValue, CsvParserSettings settings) {
		ObjectRowListProcessor processor = new ObjectRowListProcessor();
		if (isValidNullValue(nullValue)) {
			Conversion<?, ?> toNull = toNull(nullValue);
				processor.convertAll(trim(), toNull);
		}

		settings.setRowProcessor(processor);
		CsvParser parser = new CsvParser(settings);
		StringReader reader = new StringReader(input);
		parser.parse(reader);
		return processor.getRows();
	}

	static boolean isValidNullValue(String nullValue) {
		return nullValue != null && !nullValue.trim().isEmpty();
	}

	static void handleCsvException(Throwable throwable, Annotation annotation) {
		BlacklistedExceptions.rethrowIfBlacklisted(throwable);
		if (throwable instanceof PreconditionViolationException) {
			throw (PreconditionViolationException) throwable;
		}
		throw new CsvParsingException("Failed to parse CSV input configured via " + annotation, throwable);
	}

}
