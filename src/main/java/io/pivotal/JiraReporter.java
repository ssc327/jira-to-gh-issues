/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraSearchResult;
import org.joda.time.DateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static io.pivotal.JiraReporter.Predicates.*;

/**
 * @author Rossen Stoyanchev
 */
public class JiraReporter {

	private static final String SEARCH_URL = "https://jira.spring.io/rest/api/2/search?" +
			"maxResults=1000&startAt={0}&jql={jql}&fields=summary,comment,description,reporter,created";

	private static final String WAITING_FOR_TRIAGE_JQL =
			"project = SPR AND resolution = Unresolved AND fixVersion = \"Waiting for Triage\"";

	private static final List<String> COMMITTERS = Arrays.asList(
		"juergen.hoeller",	"rstoya05-aop", "sbrannen", "sdeleuze", "bclozel", "snicoll", "arjen.poutsma", "rwinch");

	private static final RestTemplate rest = new RestTemplate();


	public static void main(String[] args) {

		System.exit(0);

		runReport(committerReported().negate()
				.and(lastCommentedByCommitter()).and(lastCommentedBefore(weeksAgo(2)))); // 78

		runReport(commentedBy("juergen.hoeller"));	// 108

		runReport(commentedBy("rstoya05-aop"));	// 76

		runReport(committerCommented());	// 237 (20%)

		runReport(commented().and(committerCommented().negate()));	// 361 (30%)

		runReport(commented().negate());	// 589 (50%)

		runReport(commented().negate().and(createdBefore(yearsAgo(2))));	// 459

		runReport(commented().negate().and(createdAfter(yearsAgo(2))));	// 132

		runReportOrderedBy("votes", top(50)); // Ranging from 67 to 5


	}

	private static DateTime weeksAgo(int i) {
		return DateTime.now().minusWeeks(i);
	}

	private static DateTime yearsAgo(int i) {
		return DateTime.now().minusYears(i);
	}

	private static void runReport(Predicate<JiraIssue> predicate) {
		runReportOrderedBy("created", predicate);
	}

	private static void runReportOrderedBy(String column, Predicate<JiraIssue> predicate) {

		List<JiraIssue> result = new ArrayList<>();
		Long startAt = 0L;
		String orderBy = " ORDER BY " + column + " DESC";

		while(startAt != null) {
			ResponseEntity<JiraSearchResult> entity = rest.getForEntity(
					SEARCH_URL, JiraSearchResult.class, startAt, WAITING_FOR_TRIAGE_JQL + orderBy);
			JiraSearchResult body = entity.getBody();
			result.addAll(body.getIssues());
			startAt = body.getNextStartAt();
		}

		AtomicInteger matched = new AtomicInteger(0);
		String resultJql = result.stream()
				.filter(predicate)
				.peek(jiraIssue -> matched.incrementAndGet())
				.map(JiraIssue::getKey)
				.collect(Collectors.joining(",","key in (", ")"));

		System.out.println(matched.get() + " out of " + result.size());
		System.out.println(resultJql + orderBy);
	}

	private static Optional<JiraComment> getLastComment(JiraIssue issue) {
		return issue.getFields().getComment().getComments().stream().reduce((c, c2) -> c2);
	}


	static class Predicates {

		static Predicate<JiraIssue> createdBefore(DateTime dateTime) {
			return issue -> issue.getFields().getCreated().isBefore(dateTime);
		}

		static Predicate<JiraIssue> createdAfter(DateTime dateTime) {
			return issue -> issue.getFields().getCreated().isAfter(dateTime);
		}

		static Predicate<JiraIssue> committerReported() {
			return issue -> COMMITTERS.contains(issue.getFields().getReporter().getKey());
		}

		static Predicate<JiraIssue> commented() {
			return issue -> !issue.getFields().getComment().getComments().isEmpty();
		}

		static Predicate<JiraIssue> committerCommented() {
			return issue -> issue.getFields().getComment().getComments().stream()
					.anyMatch(comment -> COMMITTERS.contains(comment.getAuthor().getKey()));
		}

		static Predicate<JiraIssue> commentedBy(String user) {
			return issue -> issue.getFields().getComment().getComments().stream()
					.anyMatch(comment -> comment.getAuthor().getKey().equals(user));
		}

		static Predicate<JiraIssue> lastCommentedByCommitter() {
			return issue -> getLastComment(issue)
					.map(comment -> COMMITTERS.contains(comment.getAuthor().getKey()))
					.orElse(false);
		}

		static Predicate<JiraIssue> lastCommentedBefore(DateTime date) {
			return issue -> getLastComment(issue)
					.map(comment -> comment.getCreated().isBefore(date))
					.orElse(false);
		}

		static Predicate<JiraIssue> top(int maxCount) {
			AtomicInteger count = new AtomicInteger(0);
			return issue -> count.getAndIncrement() < maxCount;
		}
	}

}
