package io.ssc327.migration;

import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_MILESTONES;
import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_REPOS;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.PagedRequest;
import org.eclipse.egit.github.core.service.IssueService;

import com.google.gson.reflect.TypeToken;

public class MilestoneServiceFixed extends org.eclipse.egit.github.core.service.MilestoneService {

	/**
	 * Create milestone service
	 *
	 * @param client
	 *            cannot be null
	 */
	public MilestoneServiceFixed(GitHubClient client) {
		super(client);
	}
	
	/**
	 * Get milestones
	 *
	 * @param repository
	 * @param state
	 * @return list of milestones
	 * @throws IOException
	 */
	public List<Milestone> getMilestonesFixed(IRepositoryIdProvider repository,
			String state) throws IOException {
		String repoId = getId(repository);
		return getMilestonesFixed(repoId, state);
	}
	
	private List<Milestone> getMilestonesFixed(String id, String state)
			throws IOException {

		StringBuilder uri = new StringBuilder(SEGMENT_REPOS);
		uri.append('/').append(id);
		uri.append(SEGMENT_MILESTONES);
		PagedRequest<Milestone> request = createPagedRequest();
		if (state != null)
			request.setParams(Collections.singletonMap(
					IssueService.FILTER_STATE, state));
		request.setUri(uri).setType(new TypeToken<List<Milestone>>() {
		}.getType());
		return getAll(request);
	}

	/**
	 * Create a milestone
	 *
	 * @param repository
	 *            must be non-null
	 * @param milestone
	 *            must be non-null
	 * @return created milestone
	 * @throws IOException
	 */
	public Milestone createMilestoneFixed(IRepositoryIdProvider repository,
			Milestone milestone) throws IOException {
		String repoId = getId(repository);
		return createMilestoneFixed(repoId, milestone);
	}
	
	private Milestone createMilestoneFixed(String id, Milestone milestone)
			throws IOException {
		if (milestone == null)
			throw new IllegalArgumentException("Milestone cannot be null"); //$NON-NLS-1$

		StringBuilder uri = new StringBuilder(SEGMENT_REPOS);
		uri.append('/').append(id);
		uri.append(SEGMENT_MILESTONES);
		return client.post(uri.toString(), milestone, Milestone.class);
	}

}
