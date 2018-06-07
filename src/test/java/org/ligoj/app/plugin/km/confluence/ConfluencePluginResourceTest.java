/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.iam.IUserRepository;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.SimpleUser;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.km.KmResource;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ConfluencePluginResource}
 */
@ExtendWith(SpringExtension.class)
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

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class, DelegateNode.class },
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
	public void getVersion() throws Exception {
		prepareMockVersion();
		httpServer.start();

		final String version = resource.getVersion(subscription);
		Assertions.assertEquals("5.7.5", version);
	}

	@Test
	public void getLastVersion() throws IOException {
		final String lastVersion = resource.getLastVersion();
		Assertions.assertNotNull(lastVersion);
		Assertions.assertTrue(lastVersion.compareTo("5.8.0") > 0);
	}

	@Test
	public void link() throws IOException {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Find space
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/SPACE")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/confluence/confluence-space-SPACE.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void linkNoSpace() {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Find space -> not found
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/SPACE")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), "service:km:confluence:space", "confluence-space");
	}

	@Test
	public void validateSpaceNotFound() {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Not find space
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/SPACE")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "0");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.validateSpace(parameters);
		}), ConfluencePluginResource.PARAMETER_SPACE, "confluence-space");
	}

	@Test
	public void validateSpace() throws IOException {
		prepareMockSpaceActivity();
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "SPACE");
		checkSpaceActivityAvatar(resource.validateSpace(parameters));
	}

	@Test
	public void validateSpaceJSonError() {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Find space -> JSON error
		httpServer.stubFor(
				get(urlEqualTo("/rest/api/space/SPACE")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{error_json}")));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "SPACE");
		Assertions.assertThrows(IOException.class, () -> {
			resource.validateSpace(parameters);
		});
	}

	@Test
	public void validateSpaceActivityDefaultImage() throws IOException {
		prepareMockSpace();
		httpServer.stubFor(get(urlEqualTo("/plugins/recently-updated/changes.action?theme=social&pageSize=1&spaceKeys=SPACE"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-space-SPACE-changes-default-avatar.html").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "SPACE");
		final Space space = resource.validateSpace(parameters);
		Assertions.assertNull(checkSpaceActivity(space).getActivity().getAuthorAvatar());
	}

	@Test
	public void validateSpaceActivityImageError() throws IOException {
		prepareMockSpace();
		httpServer.stubFor(get(urlEqualTo("/plugins/recently-updated/changes.action?theme=social&pageSize=1&spaceKeys=SPACE"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/confluence/confluence-space-SPACE-changes.html").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "SPACE");
		final Space space = resource.validateSpace(parameters);
		checkSpaceActivity(space);
		Assertions.assertNull(space.getActivity().getAuthorAvatar());
	}

	@Test
	public void validateSpaceActivityNoImage() throws IOException {
		prepareMockSpace();

		// Activity
		httpServer.stubFor(get(urlEqualTo("/plugins/recently-updated/changes.action?theme=social&pageSize=1&spaceKeys=SPACE"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/confluence/confluence-space-SPACE-changes.html").getInputStream(),
								StandardCharsets.UTF_8))));

		// Avatar not found
		httpServer.stubFor(get(urlEqualTo("/some/default.png")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:km:confluence:dig");
		parameters.put(ConfluencePluginResource.PARAMETER_SPACE, "SPACE");
		final Space space = resource.validateSpace(parameters);
		checkSpaceActivity(space);
		Assertions.assertNull(space.getActivity().getAuthorAvatar());
	}

	@Test
	public void checkSubscriptionStatus() throws IOException {
		prepareMockSpaceActivity();
		httpServer.start();
		final SubscriptionStatusWithData checkSubscriptionStatus = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(checkSubscriptionStatus.getStatus().isUp());
		checkSpaceActivity((Space) checkSubscriptionStatus.getData().get("space"));
	}

	@Test
	public void checkSubscriptionStatusNoActivity() throws IOException {
		prepareMockSpace();
		httpServer.start();
		final SubscriptionStatusWithData checkSubscriptionStatus = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(checkSubscriptionStatus.getStatus().isUp());
		final Space space = (Space) checkSubscriptionStatus.getData().get("space");
		checkSpace(space);
		Assertions.assertNull(space.getActivity());
	}

	private void prepareMockSpace() throws IOException {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Find space
		httpServer.stubFor(get(urlEqualTo("/rest/api/space/SPACE")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/confluence/confluence-space-SPACE.json").getInputStream(),
						StandardCharsets.UTF_8))));
	}

	private void prepareMockSpaceActivity() throws IOException {
		prepareMockSpace();

		// Activity
		httpServer.stubFor(get(urlEqualTo("/plugins/recently-updated/changes.action?theme=social&pageSize=1&spaceKeys=SPACE"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/confluence/confluence-space-SPACE-changes.html").getInputStream(),
								StandardCharsets.UTF_8))));

		// Avatar
		httpServer.stubFor(get(urlEqualTo("/some/some.png")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toByteArray(new ClassPathResource("mock-server/confluence/default.png").getInputStream()))));
	}

	@Test
	public void checkStatus() throws IOException {
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Administration access
		httpServer.stubFor(get(urlEqualTo("/plugins/servlet/upm")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkStatusNotAdmin() throws IOException {
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		// Administration access failed
		httpServer.stubFor(get(urlEqualTo("/plugins/servlet/upm"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
		}), ConfluencePluginResource.PARAMETER_URL, "confluence-admin");
	}

	@Test
	public void checkStatusNotAuthentication() throws IOException {
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), ConfluencePluginResource.PARAMETER_URL, "confluence-login");
	}

	@Test
	public void checkStatusNotAuthenticationBadLocation() throws IOException {
		prepareMockVersion();

		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "dologin.action")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), ConfluencePluginResource.PARAMETER_URL, "confluence-login");
	}

	private void prepareMockVersion() throws IOException {
		// Version
		httpServer.stubFor(get(urlEqualTo("/forgotuserpassword.action")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-forgotuserpassword.action").getInputStream(),
						StandardCharsets.UTF_8))));
	}

	@Test
	public void checkStatusNotAuthenticationNoLocation() throws IOException {
		prepareMockVersion();

		// Authentication
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action")).willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), ConfluencePluginResource.PARAMETER_URL, "confluence-login");
	}

	private void prepareMockHome() {
		httpServer.stubFor(get(urlEqualTo("/dologin.action")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
	}

	@Test
	public void checkStatusNotAccess() {
		httpServer.stubFor(get(urlEqualTo("/forgotuserpassword.action")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), ConfluencePluginResource.PARAMETER_URL, "confluence-connection");
	}

	@Test
	public void findSpacesByNameNodeNotExists() throws IOException {
		Assertions.assertEquals(0, resource.findAllByName("service:km:confluence:any", "10000").size());
	}

	@Test
	public void findAllByName() throws IOException {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		httpServer.stubFor(get(urlEqualTo("/rest/api/space?type=global&limit=100&start=0"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-spaces.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(
				get(urlEqualTo("/rest/api/space?type=global&limit=100&start=100")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(new ClassPathResource("mock-server/confluence/confluence-spaces2.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final List<Space> projects = resource.findAllByName("service:km:confluence:dig", "p");
		Assertions.assertEquals(10, projects.size());
		checkSpace(projects.get(4));
	}

	@Test
	public void findAllByNameNotFound() throws IOException {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		httpServer.stubFor(get(urlEqualTo("/rest/api/space?type=global&limit=100&start=0"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-spaces.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(
				get(urlEqualTo("/rest/api/space?type=global&limit=100&start=100")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(new ClassPathResource("mock-server/confluence/confluence-spaces2.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final List<Space> projects = resource.findAllByName("service:km:confluence:dig", "zzz");
		Assertions.assertTrue(projects.isEmpty());
	}

	@Test
	public void testFindSpacesByNameNoRight() throws IOException {
		initSpringSecurityContext("any");
		final List<Space> projects = resource.findAllByName("service:km:confluence:dig", "SPACE");
		Assertions.assertEquals(0, projects.size());
	}

	@Test
	public void findAllByNamePage2() throws IOException {
		prepareMockHome();
		httpServer.stubFor(post(urlEqualTo("/dologin.action"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withHeader("Location", "/")));

		httpServer.stubFor(get(urlEqualTo("/rest/api/space?type=global&limit=100&start=0"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/confluence/confluence-spaces.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(
				get(urlEqualTo("/rest/api/space?type=global&limit=100&start=100")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(new ClassPathResource("mock-server/confluence/confluence-spaces2.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final List<Space> projects = resource.findAllByName("service:km:confluence:dig", "xxx");
		Assertions.assertEquals(1, projects.size());
		Assertions.assertEquals("XXX", projects.get(0).getId());
		Assertions.assertEquals("XXX - Full Name", projects.get(0).getName());
		Assertions.assertNull(projects.get(0).getActivity());
	}

	private void checkSpace(final Space space) {
		Assertions.assertEquals("SPACE", space.getId());
		Assertions.assertEquals("My Space Name", space.getName());
	}

	private Space checkSpaceActivity(final Space space) {
		checkSpace(space);
		final SpaceActivity activity = space.getActivity();
		Assertions.assertNotNull(activity);
		Assertions.assertEquals("user1", activity.getAuthor().getId());
		Assertions.assertEquals("updated 5 minutes ago", activity.getMoment());
		Assertions.assertEquals("My Page", activity.getPage());
		Assertions.assertEquals("http://localhost:8120/display/SPACE/Page", activity.getPageUrl());
		return space;
	}

	private Space checkSpaceActivityAvatar(final Space space) {
		checkSpaceActivity(space);
		final SpaceActivity activity = space.getActivity();
		Assertions.assertTrue(activity.getAuthorAvatar().startsWith("data:image/png;base64,iVBORw0K"));
		Assertions.assertEquals(3614, activity.getAuthorAvatar().length());
		return space;
	}

	@Test
	public void toSimpleUser() {
		final SimpleUser simpleUser = resource.toSimpleUser("some", "any");
		Assertions.assertEquals("some", simpleUser.getId());
		Assertions.assertEquals("First", simpleUser.getFirstName());
		Assertions.assertEquals("Last", simpleUser.getLastName());
	}

	@Test
	public void toSimpleUserUnknown() {
		final ConfluencePluginResource resource = new ConfluencePluginResource();
		resource.iamProvider = new IamProvider[] { Mockito.mock(IamProvider.class) };
		final IamConfiguration iamConfiguration = Mockito.mock(IamConfiguration.class);
		Mockito.when(resource.iamProvider[0].getConfiguration()).thenReturn(iamConfiguration);
		Mockito.when(iamConfiguration.getUserRepository()).thenReturn(Mockito.mock(IUserRepository.class));
		final SimpleUser simpleUser = resource.toSimpleUser("some", "Some People");
		Assertions.assertEquals("some", simpleUser.getId());
		Assertions.assertEquals("Some People", simpleUser.getFirstName());
		Assertions.assertNull(simpleUser.getLastName());
	}
}
