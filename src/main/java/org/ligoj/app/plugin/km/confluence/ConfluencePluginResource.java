/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.km.confluence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.SimpleUser;
import org.ligoj.app.plugin.km.KmResource;
import org.ligoj.app.plugin.km.KmServicePlugin;
import org.ligoj.app.resource.NormalizeFormat;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.VersionUtils;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.text.Format;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Confluence KM resource.
 *
 * @see "https://docs.atlassian.com/atlassian-confluence/REST/latest"
 */
@Path(ConfluencePluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
public class ConfluencePluginResource extends AbstractToolPluginResource implements KmServicePlugin {

	/**
	 * Space activity pattern for HTML markup.
	 */
	private static final Pattern ACTIVITY_PATTERN = Pattern.compile(
			"logo\"\\s*src=\"([^\"]+)\".*data-username=\"([^\"]+)\"[^>]+>([^<]+)<.*href=\"([^\"]+)\"[^>]*>([^<]+)<.*update-item-date\">([^<]+)<",
			Pattern.DOTALL);

	/**
	 * Plug-in key.
	 */
	public static final String URL = KmResource.SERVICE_URL + "/confluence";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Web site URL
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	/**
	 * Confluence space KEY (not name).
	 */
	public static final String PARAMETER_SPACE = KEY + ":space";

	/**
	 * Confluence username able to perform index.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Confluence user password able to perform index.
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * Jackson type reference for Confluence space
	 */
	private static final TypeReference<Map<String, Object>> TYPE_SPACE_REF = new TypeReference<>() {
		// Nothing to override
	};

	@Autowired
	private InMemoryPagination inMemoryPagination;

	@Autowired
	protected IamProvider[] iamProvider;

	@Autowired
	protected VersionUtils versionUtils;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * Check the server is available.
	 */
	private void validateAccess(final Map<String, String> parameters) {
		if (getVersion(parameters) == null) {
			throw new ValidationJsonException(PARAMETER_URL, "confluence-connection");
		}
	}

	/**
	 * Prepare an authenticated connection to Confluence
	 */
	private void authenticate(final Map<String, String> parameters, final CurlProcessor processor) {
		final var user = parameters.get(PARAMETER_USER);
		final var password = StringUtils.trimToEmpty(parameters.get(PARAMETER_PASSWORD));
		final var url = StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "dologin.action";
		final var requests = new ArrayList<CurlRequest>();
		requests.add(new CurlRequest(HttpMethod.GET, url, null));
		requests.add(
				new CurlRequest(HttpMethod.POST, url,
						"os_username=" + user + "&os_password=" + password
								+ "&os_destination=&atl_token=&login=Connexion",
						ConfluenceCurlProcessor.LOGIN_CALLBACK,
						"Accept:text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
		if (!processor.process(requests)) {
			throw new ValidationJsonException(PARAMETER_URL, "confluence-login", parameters.get(PARAMETER_USER));
		}
	}

	/**
	 * Validate the administration connectivity. Expect an authenticated connection.
	 */
	private void validateAdminAccess(final Map<String, String> parameters, final CurlProcessor processor) {
		final List<CurlRequest> requests = new ArrayList<>();

		// Request plugins access
		final String url = parameters.get(PARAMETER_URL);
		requests.add(
				new CurlRequest(HttpMethod.GET, StringUtils.appendIfMissing(url, "/") + "plugins/servlet/upm", null));
		if (!processor.process(requests)) {
			throw new ValidationJsonException(PARAMETER_URL, "confluence-admin", parameters.get(PARAMETER_USER));
		}
	}

	/**
	 * Validate the space configuration and return the corresponding details.
	 *
	 * @param parameters the space parameters.
	 * @return Space's details.
	 * @throws IOException When the space content cannot be read.
	 */
	protected Space validateSpace(final Map<String, String> parameters) throws IOException {
		final String baseUrl = StringUtils.removeEnd(parameters.get(PARAMETER_URL), "/");

		CurlRequest[] requests = null;

		try {
			// Validate the space key and get activity
			requests = validateSpaceInternal(parameters, "/rest/api/space/",
					"/plugins/recently-updated/changes.action?theme=social&pageSize=1&spaceKeys=");

			// Parse the space details
			final Map<String, Object> details = objectMapper.readValue(requests[0].getResponse(), TYPE_SPACE_REF);

			// Build the full space object
			return toSpace(baseUrl, details, requests[1].getResponse(), requests[0].getProcessor());
		} finally {
			// Close the processor
			closeQuietly(requests);
		}
	}

	/**
	 * CLose the related processor as needed.
	 */
	private void closeQuietly(final CurlRequest[] requests) {
		if (requests != null) {
			requests[0].getProcessor().close();
		}

	}

	/**
	 * Validate the space configuration and return the corresponding details.
	 */
	private CurlRequest[] validateSpaceInternal(final Map<String, String> parameters, final String... partialRequests) {
		final String url = StringUtils.removeEnd(parameters.get(PARAMETER_URL), "/");
		final String space = ObjectUtils.defaultIfNull(parameters.get(PARAMETER_SPACE), "0");
		final CurlRequest[] result = new CurlRequest[partialRequests.length];
		for (int i = 0; i < partialRequests.length; i++) {
			result[i] = new CurlRequest(HttpMethod.GET, url + partialRequests[i] + space, null);
			result[i].setSaveResponse(true);
		}

		// Prepare the sequence of HTTP requests to Confluence
		final ConfluenceCurlProcessor processor = new ConfluenceCurlProcessor();
		authenticate(parameters, processor);

		// Execute the requests
		processor.process(result);

		// Get the space if it exists
		if (result[0].getResponse() == null) {
			// Invalid couple PKEY and id
			throw new ValidationJsonException(PARAMETER_SPACE, "confluence-space", parameters.get(PARAMETER_SPACE));
		}
		return result;
	}

	@Override
	public void link(final int subscription) {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Validate the space key
		CurlRequest[] requests = null;
		try {
			requests = validateSpaceInternal(parameters, "/rest/api/space/");
		} finally {
			// Close the processor
			closeQuietly(requests);
		}
	}

	/**
	 * Find the spaces matching to the given criteria. Look into space key, and space name.
	 *
	 * @param node     the node to be tested with given parameters.
	 * @param criteria the search criteria.
	 * @return Matching spaces, ordered by space name, not the key.
	 * @throws IOException When the space content cannot be read.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<Space> findAllByName(@PathParam("node") final String node, @PathParam("criteria") final String criteria)
			throws IOException {
		// Check the node exists
		if (nodeRepository.findOneVisible(node, securityHelper.getLogin()) == null) {
			return Collections.emptyList();
		}

		// Get the target node parameters
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		final List<Space> result = new ArrayList<>();
		int start = 0;
		// Limit the result to 10, and search with a page size of 100
		while (addAllByName(parameters, criteria, result, start) && result.size() < 10) {
			start += 100;
		}

		return inMemoryPagination.newPage(result, PageRequest.of(0, 10)).getContent();
	}

	/**
	 * Find the spaces matching to the given criteria. Look into space key, and space name.
	 *
	 * @param parameters the node parameters.
	 * @param criteria   the search criteria.
	 * @param start      the cursor position.
	 * @return <code>true</code> when there are more spaces to fetch.
	 */
	private boolean addAllByName(final Map<String, String> parameters, final String criteria, final List<Space> result,
			final int start) throws IOException {
		// The result should be JSON, otherwise, an empty result is mocked
		final String spacesAsJson = Objects.toString(
				getConfluenceResource(parameters, "/rest/api/space?type=global&limit=100&start=" + start),
				"{\"results\":[],\"_links\":{}}");

		// Build the result from JSON
		final TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {
			// Nothing to override
		};
		final Map<String, Object> readValue = objectMapper.readValue(spacesAsJson, typeReference);
		@SuppressWarnings("unchecked") final Collection<Map<String, Object>> spaces = (Collection<Map<String, Object>>) readValue.get("results");

		// Prepare the context, an ordered set of projects
		final Format format = new NormalizeFormat();
		final String formatCriteria = format.format(criteria);

		// Get the projects and parse them
		for (final Map<String, Object> spaceRaw : spaces) {
			final Space space = toSpaceLight(spaceRaw);

			// Check the values of this project
			if (format.format(space.getName()).contains(formatCriteria)
					|| format.format(space.getId()).contains(formatCriteria)) {
				result.add(space);
			}
		}
		return ((Map<?, ?>) readValue.get("_links")).containsKey("next");
	}

	/**
	 * Map raw Confluence values to a simple details of space
	 */
	private Space toSpaceLight(final Map<String, Object> spaceRaw) {
		final Space space = new Space();
		space.setId((String) spaceRaw.get("key"));
		space.setName((String) spaceRaw.get("name"));
		return space;
	}

	/**
	 * Map API JSON Space and history values to a bean.
	 */
	private Space toSpace(final String baseUrl, final Map<String, Object> spaceRaw, final String history,
			final CurlProcessor processor) {
		final Space space = toSpaceLight(spaceRaw);
		final String hostUrl = StringUtils.removeEnd(baseUrl, URI.create(baseUrl).getPath());

		// Check the activity if available
		final Matcher matcher = ACTIVITY_PATTERN.matcher(StringUtils.defaultString(history));
		if (matcher.find()) {
			// Activity has been found
			final SpaceActivity activity = new SpaceActivity();
			getAvatar(processor, activity, hostUrl + matcher.group(1));
			activity.setAuthor(toSimpleUser(matcher.group(2), matcher.group(3)));
			activity.setPageUrl(hostUrl + matcher.group(4));
			activity.setPage(matcher.group(5));
			activity.setMoment(matcher.group(6));
			space.setActivity(activity);
		}
		return space;
	}

	/**
	 * Return the avatar PNG file from URL.
	 */
	private void getAvatar(final CurlProcessor processor, final SpaceActivity activity, final String avatarUrl) {
		if (!avatarUrl.endsWith("/default.png")) {
			// Not default URL, get the PNG bytes
			processor.process(new CurlRequest("GET", avatarUrl, null, (req, res) -> {
				// PNG to DATA URL
				if (res.getCode() == HttpServletResponse.SC_OK) {
					activity.setAuthorAvatar("data:image/png;base64,"
							+ DatatypeConverter.printBase64Binary(IOUtils.toByteArray(res.getEntity().getContent())));
				}
				return true;
			}));
		}
	}

	/**
	 * Search the given username using IAM, and if not found use the resolved Confluence display name.
	 *
	 * @param login       The user login, as requested to IAM.
	 * @param displayName The resolved Confluence display name, used when the user has not been found in IAM.
	 * @return A {@link SimpleUser} instance representing at best effort the requested user.
	 */
	protected SimpleUser toSimpleUser(final String login, final String displayName) {
		return Optional.ofNullable(getUser(login)).map(u -> {
			final SimpleUser user = new SimpleUser();
			u.copy(user);
			return user;
		}).orElseGet(() -> {
			final SimpleUser user = new SimpleUser();
			// Painful trying to separate first/last name
			user.setId(login);
			user.setFirstName(displayName);
			return user;
		});
	}

	/**
	 * Request IAM provider to get user details.
	 *
	 * @param login The requested user login.
	 * @return Either the resolved instance, either <code>null</code> when not found.
	 */
	protected SimpleUser getUser(final String login) {
		return iamProvider[0].getConfiguration().getUserRepository().findById(login);
	}

	/**
	 * Return a Confluence's resource. Return <code>null</code> when the resource is not found.
	 */
	private String getConfluencePublicResource(final Map<String, String> parameters, final String resource) {
		return getConfluenceResource(new CurlProcessor(), parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a Confluence's resource after an authentication. Return <code>null</code> when the resource is not found.
	 */
	private String getConfluenceResource(final Map<String, String> parameters, final String resource) {
		final ConfluenceCurlProcessor processor = new ConfluenceCurlProcessor();
		authenticate(parameters, processor);
		return getConfluenceResource(processor, parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a Jenkins's resource. Return <code>null</code> when the resource is not found.
	 */
	private String getConfluenceResource(final CurlProcessor processor, final String url, final String resource) {
		// Get the resource using the preempted authentication
		final CurlRequest request = new CurlRequest(HttpMethod.GET, StringUtils.removeEnd(url, "/") + resource, null);
		request.setSaveResponse(true);

		// Execute the requests
		processor.process(request);
		processor.close();
		return request.getResponse();
	}

	@Override
	public String getKey() {
		return ConfluencePluginResource.KEY;
	}

	@Override
	public String getVersion(final Map<String, String> parameters) {
		final String page = StringUtils
				.trimToEmpty(getConfluencePublicResource(parameters, "/forgotuserpassword.action"));
		final String ajsMeta = "ajs-version-number\" content=";
		final int versionIndex = Math.min(page.length(), page.indexOf(ajsMeta) + ajsMeta.length() + 1);
		return StringUtils
				.trimToNull(page.substring(versionIndex, Math.max(versionIndex, page.indexOf('\"', versionIndex))));
	}

	@Override
	public String getLastVersion() throws IOException {
		// Get the download json from the default repository
		return versionUtils.getLatestReleasedVersionName("https://jira.atlassian.com", "CONF");
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Status is UP <=> Administration access is UP (if defined)
		validateAccess(parameters);

		try (CurlProcessor processor = new ConfluenceCurlProcessor()) {
			// Check the user can log in to Confluence
			authenticate(parameters, processor);

			// Check the user has enough rights to access to the plugin page
			validateAdminAccess(parameters, processor);
		}
		return true;
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws IOException {
		final SubscriptionStatusWithData data = new SubscriptionStatusWithData();
		data.put("space", validateSpace(parameters));
		return data;
	}
}
