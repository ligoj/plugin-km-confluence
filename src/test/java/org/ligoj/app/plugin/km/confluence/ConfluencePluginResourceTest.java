package org.ligoj.app.plugin.km.confluence;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.km.KmResource;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link ConfluencePluginResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ConfluencePluginResourceTest extends AbstractServerTest {
	@Autowired
	private ConfluencePluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ParameterValueResource pvResource;

	protected int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is
	 * only one subscription for a service.
	 */
	protected int getSubscription(final String project) {
		return getSubscription(project, KmResource.SERVICE_KEY);
	}

	@Test
	public void delete() throws Exception {
		resource.delete(subscription, false);
	}

	@Test
	public void testGetVersion() throws Exception {
		prepareMockVersion();
		httpServer.start();

		final String version = resource.getVersion(subscription);
		Assert.assertEquals("5.7.5", version);
	}

	@Test
	public void testGetLastVersion() throws Exception {
		final String lastVersion = resource.getLastVersion();
		Assert.assertNotNull(lastVersion);
		Assert.assertTrue(lastVersion.compareTo("5.8.0") > 0);
	}

	@Test
	public void link() throws Exception {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Find space
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/INDUS?expand=description.plain")).willReturn(aResponse()
				.withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-space-INDUS.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void testValidateSpaceNotFound() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(ConfluencePluginResource.PARAMETER_SPACE, "confluence-space"));
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Not find space
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/INDUS?expand=description.plain"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "0");
		resource.validateSpace(parameters);
	}

	@Test
	public void testValidateSpace() throws Exception {
		prepareMockSpace();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "INDUS");
		checkSpace(resource.validateSpace(parameters));
	}

	@Test
	public void testCheckSubscriptionStatus() throws Exception {
		prepareMockSpace();
		Assert.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription))
				.getStatus().isUp());
	}

	private void prepareMockSpace() throws IOException {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Find space
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/INDUS?expand=description.plain")).willReturn(aResponse()
				.withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-space-INDUS.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	public void testCheckStatus() throws Exception {
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Administration access
		httpServer
				.stubFor(get(urlEqualTo("/plugins/servlet/upm")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void testCheckStatusNotAdmin() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(ConfluencePluginResource.PARAMETER_URL, "confluence-admin"));

		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Administration access failed
		httpServer.stubFor(get(urlEqualTo("/plugins/servlet/upm"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));
		httpServer.start();
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void testCheckStatusNotAuthentication() throws Exception {
		assertConnectionFailed();
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	public void testCheckStatusNotAuthenticationBadLocation() throws Exception {
		assertConnectionFailed();
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action")).willReturn(
				aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "dologin.action")));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	private void prepareMockVersion() throws IOException {
		// Version
		httpServer
				.stubFor(get(urlEqualTo("/forgotuserpassword.action"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
								.withBody(IOUtils.toString(new ClassPathResource(
										"mock-server/confluence/confluence-forgotuserpassword.action").getInputStream(),
										StandardCharsets.UTF_8))));
	}

	private void assertConnectionFailed() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(ConfluencePluginResource.PARAMETER_URL, "confluence-login"));
	}

	@Test
	public void testCheckStatusNotAuthenticationNoLocation() throws Exception {
		assertConnectionFailed();
		prepareMockVersion();

		// Authentication
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	private void prepareMockHome() {
		httpServer.stubFor(get(urlEqualTo("/dologin.action")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
	}

	@Test
	public void testCheckStatusNotAccess() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(ConfluencePluginResource.PARAMETER_URL, "confluence-connection"));
		httpServer.stubFor(get(urlEqualTo("/forgotuserpassword.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	public void testFindSpacesByName() throws Exception {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		httpServer.stubFor(get(urlEqualTo("/rest/api/space?expand=description.plain&type=global&limit=100&start=0"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/confluence/confluence-spaces.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/rest/api/space?expand=description.plain&type=global&limit=100&start=100"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-spaces2.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();

		final List<IDescribableBean<String>> projects = resource.findAllByName("service:km:confluence:dig", "d");
		Assert.assertEquals(10, projects.size());
		checkSpace(projects.get(7));
	}

	@Test
	public void testFindSpacesByNameNotFound() throws Exception {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		httpServer.stubFor(get(urlEqualTo("/rest/api/space?expand=description.plain&type=global&limit=100&start=0"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/confluence/confluence-spaces.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/rest/api/space?expand=description.plain&type=global&limit=100&start=100"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-spaces2.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();

		final List<IDescribableBean<String>> projects = resource.findAllByName("service:km:confluence:dig", "zzz");
		Assert.assertTrue(projects.isEmpty());
	}

	@Test
	public void testFindSpacesByNamePage2() throws Exception {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		httpServer.stubFor(get(urlEqualTo("/rest/api/space?expand=description.plain&type=global&limit=100&start=0"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/confluence/confluence-spaces.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/rest/api/space?expand=description.plain&type=global&limit=100&start=100"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-spaces2.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();

		final List<IDescribableBean<String>> projects = resource.findAllByName("service:km:confluence:dig", "xxx");
		Assert.assertEquals(1, projects.size());
		Assert.assertEquals("XXX", projects.get(0).getId());
	}

	private void checkSpace(final IDescribableBean<String> space) {
		Assert.assertEquals("INDUS", space.getId());
		Assert.assertEquals("Chantier Technical Solutions", space.getName());
		Assert.assertEquals("Choix et implémentation des outils pour l'industrialisation", space.getDescription());
	}
}
