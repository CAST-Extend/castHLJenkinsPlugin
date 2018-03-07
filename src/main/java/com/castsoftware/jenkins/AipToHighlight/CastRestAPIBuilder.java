package com.castsoftware.jenkins.AipToHighlight;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.castsoftware.restapi.CastRest;
import com.castsoftware.restapi.JsonResponse;
import com.castsoftware.restapi.RestException;
import com.castsoftware.restapi.pojo.Aad;
import com.castsoftware.restapi.pojo.Application;
import com.castsoftware.restapi.pojo.ApplicationResult;
import com.castsoftware.restapi.pojo.Metric;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.Gson;

public class CastRestAPIBuilder extends Builder {

	private final String restUrl;
	private final String credentialsId;
	private final String aad;
	private final String applName;

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public CastRestAPIBuilder(String restUrl, String credentialsId, String aad, String applName) {
		this.restUrl = restUrl;
		this.credentialsId = credentialsId;
		this.aad = aad;
		this.applName = applName;
	}

	public String getRestUrl() {
		return restUrl;
	}

	public String getCredentialsId() {
		return credentialsId;
	}

	public String getAad() {
		return aad;
	}

	public String getApplName() {
		return applName;
	}

	public Integer getApplNameAsInteger() {
		return Integer.parseInt(applName);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException {
		boolean validateBuild = true;
		listener.getLogger().println(String.format("Highlight Rest Url: %s", restUrl));
		listener.getLogger().println(String.format("Application: %s", applName));

		JsonResponse jsonResponse = CastRest.QueryAPI(String.format("%s/runHighlight?applName=%s", restUrl, applName));
		int applId = jsonResponse.getCode();
		String message = jsonResponse.getJsonString();

		Gson gson = new Gson();

		if (applId == 200) {
			JsonResponse status = gson.fromJson(jsonResponse.getJsonString(), JsonResponse.class);
			applId = status.getCode();
			if (applId < 0) {
				validateBuild = false;
				listener.getLogger().println(String.format("Error running anaslysis: %s", status.getJsonString()));
			} else {
				getRunStatus(applId, listener);
			}

		} else {
			validateBuild = false;
			listener.getLogger().println(String.format("Error running anaslysis: %s", message));
		}

		return validateBuild;
	}

	private void getRunStatus(int runId, BuildListener listener) {
		boolean isRunning = true;
		Gson gson = new Gson();
		String applStatus = "";
		while (isRunning) {
			JsonResponse jsonResponse = CastRest.QueryAPI(String.format("%s/runStatus?runId=%d", restUrl, runId));
			if (jsonResponse.getCode() == 200) {
				JsonResponse status = gson.fromJson(jsonResponse.getJsonString(), JsonResponse.class);
				if (status.getCode() < 0) {
					listener.getLogger().println(String.format("Error runinning analysis: %s", status.getJsonString()));
					isRunning = false;
				} else {
					int code = status.getCode();
					String jstr = status.getJsonString();
					if (jstr != null && !applStatus.equals(jstr)) {
						applStatus = jstr;
						if (code==0)
						{
							listener.getLogger().println(applStatus);							
						} else {
							listener.getLogger().println(String.format("Analysis complete: %s", status.getCode()<0?"Failed":"Success"));
							isRunning = false;
						}
					}
				}

			} else {
				listener.getLogger()
						.println(String.format("Unable to retrieve run status: %s", jsonResponse.getJsonString()));
				isRunning = false;
			}

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				listener.getLogger().println(e.getMessage());
			}
		}

	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link CastRestAPIBuilder}. Used as a singleton. The class
	 * is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an
				// extension point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		// private boolean useFrench;

		/**
		 * In order to load the persisted global configuration, you have to call
		 * load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "AIP to Highlight";
		}

		public FormValidation doTestConnection(@AncestorInPath Item context,
				@QueryParameter("restUrl") final String restUrl,
				@QueryParameter("credentialsId") final String credentialsId) throws IOException, ServletException {
			try {
				// StandardUsernamePasswordCredentials credentials =
				// lookupCredentials(context, credentialsId, restUrl);

				int responseStatus = CastRest.testConnection(restUrl);

				switch (responseStatus) {
				case -200:
					return FormValidation.error("Connected but that address does not host a Cast REST API!");
				case 200:
					return FormValidation.ok("Success");
				case 470:
					return FormValidation.error("Invalid Login/Password");
				default:
					return FormValidation.error(String.format("Unknown Response Code - %d", responseStatus));
				}
			} catch (Exception e) {
				return FormValidation.error("Exception: " + e.getMessage());
			}
		}

		private static StandardUsernamePasswordCredentials lookupCredentials(Item context, String credentialsId,
				String repoURL) {
			List<StandardUsernamePasswordCredentials> matchingCredentials = CredentialsProvider.lookupCredentials(
					StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM,
					URIRequirementBuilder.fromUri(repoURL).build());
			return CredentialsMatchers.firstOrNull(matchingCredentials, CredentialsMatchers.withId(credentialsId));

		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String remote) {
			if (context == null || !context.hasPermission(Item.CONFIGURE)) {
				return new ListBoxModel();
			}
			return fillCredentialsIdItems(context, remote);
		}

		public ListBoxModel fillCredentialsIdItems(@Nonnull Item context, String remote) {
			List<DomainRequirement> domainRequirements;
			if (remote == null) {
				domainRequirements = Collections.<DomainRequirement>emptyList();
			} else {
				domainRequirements = URIRequirementBuilder.fromUri(remote.trim()).build();
			}
			return new StandardListBoxModel().withEmptySelection().withMatching(
					CredentialsMatchers
							.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)),
					CredentialsProvider.lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,
							domainRequirements));
		}

		public ListBoxModel doFillApplNameItems(@AncestorInPath Item context,
				@QueryParameter("restUrl") final String restUrl) {
			ListBoxModel m = new ListBoxModel();

			List<String> apps = new ArrayList<String>();
			apps = CastRest.listApplications(restUrl);
			for (String app : apps) {
				m.add(app);
			}
			return m;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			// useFrench = formData.getBoolean("useFrench");
			// ^Can also use req.bindJSON(this, formData);
			// (easier when there are many fields; need set* methods for this,
			// like setUseFrench)
			save();
			return super.configure(req, formData);
		}

	}
}
