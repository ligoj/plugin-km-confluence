package org.ligoj.app.plugin.km.confluence;

import java.io.IOException;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.plugin.km.KmResource;
import org.ligoj.app.plugin.km.KmServicePlugin;
import org.ligoj.app.resource.NormalizeFormat;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.CurlRequest;
import org.ligoj.app.resource.plugin.VersionUtils;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	 * Confluence user name able to perform index.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Confluence user password able to perform index.
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * Jackson type reference for Confluence space
	 */
	private static final TypeReference<Map<String, Object>> TYPE_SPACE_REF = new TypeReference<Map<String, Object>>() {
		// Nothing to override
	};

	@Autowired
	private InMemoryPagination inMemoryPagination;

	@Autowired
	protected VersionUtils versionUtils;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private SecurityHelper securityHelper;

	/**
	 * Check the server is available.
	 */
	private void validateAccess(final Map<String, String> parameters) throws Exception {
		if (getVersion(parameters) == null) {
			throw new ValidationJsonException(PARAMETER_URL, "confluence-connection");
		}
	}

	/**
	 * Prepare an authenticated connection to Confluence
	 */
	protected void authenticate(final Map<String, String> parameters, final CurlProcessor processor) {
		final String user = parameters.get(PARAMETER_USER);
		final String password = StringUtils.trimToEmpty(parameters.get(PARAMETER_PASSWORD));
		final String url = StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "dologin.action";
		final List<CurlRequest> requests = new ArrayList<>();
		requests.add(new CurlRequest(HttpMethod.GET, url, null));
		requests.add(new CurlRequest(HttpMethod.POST, url,
				"os_username=" + user + "&os_password=" + password + "&os_destination=&atl_token=&login=Connexion",
				ConfluenceCurlProcessor.LOGIN_CALLBACK, "Accept:text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
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
		requests.add(new CurlRequest(HttpMethod.GET, StringUtils.appendIfMissing(url, "/") + "plugins/servlet/upm", null));
		if (!processor.process(requests)) {
			throw new ValidationJsonException(PARAMETER_URL, "confluence-admin", parameters.get(PARAMETER_USER));
		}
	}

	/**
	 * Validate the space configuration.
	 * 
	 * @param parameters
	 *            the space parameters.
	 * @return project description.
	 */
	protected IDescribableBean<String> validateSpace(final Map<String, String> parameters) throws IOException {

		// Get the space if it exists
		final String spaceAsJson = getConfluenceResource(parameters,
				"/rest/api/space/" + ObjectUtils.defaultIfNull(parameters.get(PARAMETER_SPACE), "0") + "?expand=description.plain");
		if (spaceAsJson == null) {
			// Invalid couple PKEY and id
			throw new ValidationJsonException(PARAMETER_SPACE, "confluence-space", parameters.get(PARAMETER_SPACE));
		}
		// Build the result from JSON
		return toSpace(new ObjectMapper().readValue(spaceAsJson, TYPE_SPACE_REF));
	}

	@Override
	public void link(final int subscription) throws Exception {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Validate the space key
		validateSpace(parameters);
	}

	/**
	 * Find the spaces matching to the given criteria. Look into space key, and space name.
	 * 
	 * @param node
	 *            the node to be tested with given parameters.
	 * @param criteria
	 *            the search criteria.
	 * @return Matching spaces, ordered by space name, not the the key.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<IDescribableBean<String>> findAllByName(@PathParam("node") final String node, @PathParam("criteria") final String criteria)
			throws IOException {
		// Check the node exists
		if (nodeRepository.findOneVisible(node, securityHelper.getLogin()) == null) {
			return Collections.emptyList();
		}

		// Get the target node parameters
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		final List<IDescribableBean<String>> result = new ArrayList<>();
		int start = 0;
		// Limit the result to 10
		while (addAllByName(parameters, criteria, result, start) && result.size() < 10) {
			start += 100;
		}

		return inMemoryPagination.newPage(result, new PageRequest(0, 10)).getContent();
	}

	/**
	 * Find the spaces matching to the given criteria. Look into space key, and space name.
	 * 
	 * @param parameters
	 *            the node parameters.
	 * @param criteria
	 *            the search criteria.
	 * @param start
	 *            the cursor position.
	 * @return <code>true</code> when there are more spaces to fetch.
	 */
	private boolean addAllByName(final Map<String, String> parameters, final String criteria, final List<IDescribableBean<String>> result,
			final int start) throws IOException {
		// The result should be JSON, otherwise, an empty result is mocked
		final String spacesAsJson = StringUtils.defaultString(
				getConfluenceResource(parameters, "/rest/api/space?expand=description.plain&type=global&limit=100&start=" + start),
				"{\"results\":[],\"_links\":{}}");

		// Build the result from JSON
		final TypeReference<Map<String, Object>> typeReference = new TypeReference<Map<String, Object>>() {
			// Nothing to override
		};
		final Map<String, Object> readValue = new ObjectMapper().readValue(spacesAsJson, typeReference);
		@SuppressWarnings("unchecked")
		final Collection<Map<String, Object>> spaces = (Collection<Map<String, Object>>) readValue.get("results");

		// Prepare the context, an ordered set of projects
		final Format format = new NormalizeFormat();
		final String formatCriteria = format.format(criteria);

		// Get the projects and parse them
		for (final Map<String, Object> spaceRaw : spaces) {
			final IDescribableBean<String> space = toSpace(spaceRaw);

			// Check the values of this project
			if (format.format(space.getName()).contains(formatCriteria)
					|| format.format(StringUtils.defaultString(space.getDescription())).contains(formatCriteria)) {
				result.add(space);
			}
		}
		return ((Map<?, ?>) readValue.get("_links")).containsKey("next");
	}

	/**
	 * Map raw Confluence values to a bean
	 */
	private IDescribableBean<String> toSpace(final Map<String, Object> spaceRaw) {
		final IDescribableBean<String> space = new DescribedBean<>();
		space.setId((String) spaceRaw.get("key"));
		space.setName((String) spaceRaw.get("name"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> descriptionRaw = MapUtils.emptyIfNull((Map<String, Object>) spaceRaw.get("description"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> descriptionPlainRaw = MapUtils.emptyIfNull((Map<String, Object>) descriptionRaw.get("plain"));
		space.setDescription((String) descriptionPlainRaw.get("value"));
		return space;
	}

	/**
	 * Return a Confluence's resource. Return <code>null</code> when the resource is not found.
	 */
	protected String getConfluencePublicResource(final Map<String, String> parameters, final String resource) {
		return getConfluenceResource(new CurlProcessor(), parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a Confluence's resource after an authentication. Return <code>null</code> when the resource is not found.
	 */
	protected String getConfluenceResource(final Map<String, String> parameters, final String resource) {
		final ConfluenceCurlProcessor processor = new ConfluenceCurlProcessor();
		authenticate(parameters, processor);
		return getConfluenceResource(processor, parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a Jenkins's resource. Return <code>null</code> when the resource is not found.
	 */
	protected String getConfluenceResource(final CurlProcessor processor, final String url, final String resource) {
		// Get the resource using the preempted authentication
		final CurlRequest request = new CurlRequest(HttpMethod.GET, StringUtils.removeEnd(url, "/") + resource, null);
		request.setSaveResponse(true);
		final List<CurlRequest> requests = new ArrayList<>();
		requests.add(request);

		// Execute the requests
		processor.process(requests);
		processor.close();
		return request.getResponse();
	}

	@Override
	public String getKey() {
		return ConfluencePluginResource.KEY;
	}

	@Override
	public String getVersion(final Map<String, String> parameters) throws Exception {
		final String page = StringUtils.trimToEmpty(getConfluencePublicResource(parameters, "/forgotuserpassword.action"));
		final String ajsMeta = "ajs-version-number\" content=";
		final int versionIndex = Math.min(page.length(), page.indexOf(ajsMeta) + ajsMeta.length() + 1);
		return StringUtils.trimToNull(page.substring(versionIndex, Math.max(versionIndex, page.indexOf('\"', versionIndex))));
	}

	@Override
	public String getLastVersion() throws Exception {
		// Get the download json from the default repository
		return versionUtils.getLatestReleasedVersionName("https://jira.atlassian.com", "CONF");
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) throws Exception {
		// Status is UP <=> Administration access is UP (if defined)
		validateAccess(parameters);

		final CurlProcessor processor = new ConfluenceCurlProcessor();
		try {
			// Check the user can log-in to Confluence
			authenticate(parameters, processor);

			// Check the user has enough rights to access to the plugin page
			validateAdminAccess(parameters, processor);
		} finally {
			processor.close();
		}
		return true;
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws Exception {
		validateSpace(parameters);
		return new SubscriptionStatusWithData();
	}
}
