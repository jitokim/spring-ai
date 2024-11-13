/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.rag.analysis.query.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Transformer that handles translation of the input query to a target language using a
 * large language model. It's aimed at optimizing similarity searches by translating a
 * query into a language supported by the document store.
 *
 * <p>
 * Example usage: <pre>{@code
 * QueryTransformer transformer = TranslationQueryTransformer.builder()
 *    .chatClientBuilder(chatClientBuilder)
 *    .targetLanguage("english")
 *    .build();
 * Query transformedQuery = transformer.transform(new Query("Hvad er Danmarks hovedstad?"));
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class TranslationQueryTransformer implements QueryTransformer {

	private static final Logger logger = LoggerFactory.getLogger(TranslationQueryTransformer.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			Given a user query, translate it to {targetLanguage}.
			If the query is already in {targetLanguage}, return it unchanged.
			If you don't know the language of the query, return it unchanged.
			Do not add explanations nor any other text.

			Original query: {query}

			Translated query:
			""");

	private final ChatClient chatClient;

	private final PromptTemplate promptTemplate;

	private final String targetLanguage;

	public TranslationQueryTransformer(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate,
			String targetLanguage) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
		Assert.hasText(targetLanguage, "targetLanguage cannot be null or empty");

		this.chatClient = chatClientBuilder.build();
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.targetLanguage = targetLanguage;

		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "targetLanguage", "query");
	}

	@Override
	public Query transform(Query query) {
		Assert.notNull(query, "query cannot be null");

		logger.debug("Translating query to target language: {}", this.targetLanguage);

		var translatedQuery = this.chatClient.prompt()
			.user(user -> user.text(this.promptTemplate.getTemplate())
				.param("targetLanguage", targetLanguage)
				.param("query", query.text()))
			.options(ChatOptionsBuilder.builder().withTemperature(0.0).build())
			.call()
			.content();

		if (!StringUtils.hasText(translatedQuery)) {
			logger.warn("Query translation result is null/empty. Returning the input query unchanged.");
			return query;
		}

		return new Query(translatedQuery);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.Builder chatClientBuilder;

		private PromptTemplate promptTemplate;

		private String targetLanguage;

		private Builder() {
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder promptTemplate(PromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		public Builder targetLanguage(String targetLanguage) {
			this.targetLanguage = targetLanguage;
			return this;
		}

		public TranslationQueryTransformer build() {
			return new TranslationQueryTransformer(this.chatClientBuilder, this.promptTemplate, this.targetLanguage);
		}

	}

}