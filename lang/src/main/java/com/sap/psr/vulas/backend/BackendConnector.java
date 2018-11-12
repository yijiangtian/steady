package com.sap.psr.vulas.backend;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;

import com.sap.psr.vulas.backend.requests.BasicHttpRequest;
import com.sap.psr.vulas.backend.requests.ConditionalHttpRequest;
import com.sap.psr.vulas.backend.requests.ContentCondition;
import com.sap.psr.vulas.backend.requests.HttpRequest;
import com.sap.psr.vulas.backend.requests.HttpRequestList;
import com.sap.psr.vulas.backend.requests.PutLibraryCondition;
import com.sap.psr.vulas.backend.requests.StatusCondition;
import com.sap.psr.vulas.core.util.CoreConfiguration;
import com.sap.psr.vulas.goals.GoalContext;
import com.sap.psr.vulas.shared.connectivity.PathBuilder;
import com.sap.psr.vulas.shared.connectivity.Service;
import com.sap.psr.vulas.shared.enums.ConstructChangeType;
import com.sap.psr.vulas.shared.enums.ProgrammingLanguage;
import com.sap.psr.vulas.shared.json.JacksonUtil;
import com.sap.psr.vulas.shared.json.model.AffectedLibrary;
import com.sap.psr.vulas.shared.json.model.Application;
import com.sap.psr.vulas.shared.json.model.Artifact;
import com.sap.psr.vulas.shared.json.model.Bug;
import com.sap.psr.vulas.shared.json.model.BugChangeList;
import com.sap.psr.vulas.shared.json.model.ConstructChange;
import com.sap.psr.vulas.shared.json.model.ConstructId;
import com.sap.psr.vulas.shared.json.model.Dependency;
import com.sap.psr.vulas.shared.json.model.Library;
import com.sap.psr.vulas.shared.json.model.Space;
import com.sap.psr.vulas.shared.json.model.VulnerableDependency;
import com.sap.psr.vulas.shared.util.FileSearch;
import com.sap.psr.vulas.shared.util.StringList;
import com.sap.psr.vulas.shared.util.StringList.CaseSensitivity;
import com.sap.psr.vulas.shared.util.StringList.ComparisonMode;
import com.sap.psr.vulas.shared.util.VulasConfiguration;


/**
 * To be used for interacting with the RESTful backend API.
 *
 */
public class BackendConnector {

	private static final Log log = LogFactory.getLog(BackendConnector.class);

	/* Singleton instance. */
	private static BackendConnector instance = null;

	/**
	 * Cache of change lists for bugs, relevant for given applications.
	 * The map is populated in method {@link BackendConnector#getAppBugs(Application)}.
	 */
	private Map<Application, Map<String, Set<com.sap.psr.vulas.shared.json.model.ConstructId>>> cacheBugChangeLists = new HashMap<Application, Map<String, Set<ConstructId>>>();
	
	/**
	 * Cache the presence of the space in the backend.
	 */
	private Map<Space, Boolean> cacheSpaceExistanceCheck = new HashMap<Space, Boolean>();

	/**
	 * Cache the presence of the application in the backend.
	 */
	private Map<Application, Boolean> cacheAppExistanceCheck = new HashMap<Application, Boolean>();
	
	/**
	 * Cache app dependencies.
	 */
	private Map<Application, Set<Dependency>> cacheAppDependencies = new HashMap<Application, Set<Dependency>>();

	/**
	 * Cache app constructs.
	 */
	private Map<Application, Set<ConstructId>> cacheAppConstructs = new HashMap<Application, Set<ConstructId>>();


	protected BackendConnector() { super(); }

	public synchronized static BackendConnector getInstance() {
		if(instance==null) instance = new BackendConnector();
		return instance;
	}

	//TODO: Make all caches dependent on space and/or app!
	public void cleanCache() {
		//if(!this.cacheBugChangeLists.isEmpty() || !this.cacheAppExistanceCheck.isEmpty()) {
			BackendConnector.log.info("Deleting cache: [" + this.cacheBugChangeLists.size() + "] bug change lists, [" + this.cacheAppExistanceCheck.size() + "] app existance");
			this.cacheBugChangeLists = new HashMap<Application, Map<String, Set<ConstructId>>>();
			this.cacheSpaceExistanceCheck = new HashMap<Space, Boolean>();
			this.cacheAppExistanceCheck = new HashMap<Application, Boolean>();
			this.cacheAppDependencies = new HashMap<Application, Set<Dependency>>();
			this.cacheAppConstructs = new HashMap<Application, Set<ConstructId>>();
		//}
	}
	
	// ---------------------------------- SPACE-RELATED CALLS
	
	public boolean isSpaceExisting(GoalContext _goal_context, Space _space) throws BackendConnectionException {
		Boolean exists = false;
		if(!cacheSpaceExistanceCheck.containsKey(_space)) {
			final HttpResponse response = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.space(_space), null)
					.setGoalContext(_goal_context)
					.send();
			exists = response.isOk();
			cacheSpaceExistanceCheck.put(_space, exists);
		}
		return cacheSpaceExistanceCheck.get(_space);
	}
	
	public Space createSpace(GoalContext _goal_context, Space _space) throws BackendConnectionException {
		final BasicHttpRequest r = new BasicHttpRequest(HttpMethod.POST, PathBuilder.spaces(), null); 
		r.setGoalContext(_goal_context);
		r.setPayload(JacksonUtil.asJsonString(_space), null, true);
		final HttpResponse response = r.send();
		
		// Read and return the response to the caller (including the server-side generated space token)
		Space created_space = null;
		if(response!=null && response.isCreated()) {
			try {
				created_space = (Space)JacksonUtil.asObject(response.getBody(), Space.class);
			} catch (Exception e) {
				throw new BackendConnectionException("Cannot deseriale the newly created space: " + e.getMessage(), e);
			}
		}
		return created_space;
	}
	
	public void modifySpace(GoalContext _goal_context, Space _space) throws BackendConnectionException {
		final BasicHttpRequest r = new BasicHttpRequest(HttpMethod.PUT, PathBuilder.space(_space), null); 
		r.setGoalContext(_goal_context);
		r.setPayload(JacksonUtil.asJsonString(_space), null, true);
		r.send();
	}
	
	public void cleanSpace(GoalContext _goal_context, Space _space) throws BackendConnectionException {
		final Map<String,String> params = new HashMap<String,String>();
		params.put("clean", "true");
		final BasicHttpRequest r = new BasicHttpRequest(HttpMethod.POST, PathBuilder.space(_space), params);
		r.setGoalContext(_goal_context);
		r.send();
	}
	
	public void deleteSpace(GoalContext _goal_context, Space _space) throws BackendConnectionException {
		final BasicHttpRequest r = new BasicHttpRequest(HttpMethod.DELETE, PathBuilder.space(_space), null);
		r.setGoalContext(_goal_context);
		r.send();
	}
	
	// ---------------------------------- APP-RELATED CALLS

	public boolean isAppExisting(GoalContext _goal_context, Application _app) throws BackendConnectionException {
		Boolean exists = false;
		if(!cacheAppExistanceCheck.containsKey(_app)) {
			final HttpResponse response = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.app(_app), null)
					.setGoalContext(_goal_context)
					.send();
			exists = response!=null && response.isOk();
			cacheAppExistanceCheck.put(_app, exists);
		}
		return cacheAppExistanceCheck.get(_app);
	}

	/**
	 * @param _goal_context TODO
	 * @param _app
	 * @param _clean_history
	 * @param _clean_all_versions
	 * @throws BackendConnectionException
	 */
	public void cleanApp(GoalContext _goal_context, Application _app, boolean _clean_history) throws BackendConnectionException {
		if(this.isAppExisting(_goal_context, _app)) {
			final Map<String,String> params = new HashMap<String,String>();
			params.put("clean", "true");
			params.put("cleanGoalHistory", Boolean.toString(_clean_history));
			final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.app(_app), params); 
			req.setGoalContext(_goal_context);
			req.send();
		}
	}

	/**
	 * @param _goal_context TODO
	 */
	public void purgeAppVersions(GoalContext _goal_context, Application _app, int _keep) throws BackendConnectionException {
		final Map<String,String> params = new HashMap<String,String>();
		params.put("keep", Integer.toString(_keep));
		params.put("mode", "VERSIONS"); // Mode DAYS is not yet support on client-side
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.DELETE, PathBuilder.artifact(_app), params); 
		req.setGoalContext(_goal_context);
		req.send();
	}

	public void uploadApp(GoalContext _goal_context, Application _app) throws BackendConnectionException {
		final String json = JacksonUtil.asJsonString(_app);
		
		// The request depending on whose result either POST or PUT will be called
		final BasicHttpRequest cond_req = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.app(_app), null);
		cond_req.setGoalContext(_goal_context);
		
		final HttpRequestList req_list = new HttpRequestList();
		final Map<String,String> params = new HashMap<String,String>();
		params.put("skipResponseBody", "true");
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.POST, PathBuilder.apps(), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_NOT_FOUND))
				.setPayload(json, null, true)
				.setGoalContext(_goal_context)
				);
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.app(_app), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
				.setPayload(json, null, true)
				.setGoalContext(_goal_context)
				);
		req_list.send();
	}

	public boolean uploadReachableConstructs(GoalContext _goal_context, Application _app, String _lib_digest, String _json) throws BackendConnectionException {
		if(this.isAppExisting(_goal_context, _app)) {
			final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.appReachableConstructs(_app, _lib_digest), null);
			req.setGoalContext(_goal_context);
			req.setPayload(_json,  null,  true);
			final HttpResponse response = req.send();
			return response==null || response.isOk();
		}
		else {
			return false;
		}
	}

	public boolean uploadTouchPoints(GoalContext _goal_context, Application _app, String _lib_digest, String _json) throws BackendConnectionException {
		if(this.isAppExisting(_goal_context, _app)) {
			final Map<String,String> params = new HashMap<String,String>();
			params.put("skipResponseBody", "true");
			final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.appTouchPoints(_app, _lib_digest), params);
			req.setGoalContext(_goal_context);
			req.setPayload(_json,  null,  true);
			final HttpResponse response = req.send();
			return response==null || response.isOk();
		}
		else {
			return false;
		}
	}

	public Set<ConstructId> getAppConstructIds(GoalContext _ctx, Application _app) throws BackendConnectionException {
		if(!cacheAppConstructs.containsKey(_app)) {
			final boolean app_exists = this.isAppExisting(_ctx, _app);
			final Set<ConstructId> constructs = new HashSet<ConstructId>();
			if(app_exists) {
				final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.appConstructIds(_app), null)
						.setGoalContext(_ctx)
						.send()
						.getBody();
				final ConstructId[] backend_app_construct_ids = (ConstructId[])JacksonUtil.asObject(json, ConstructId[].class);
				for(ConstructId backend_app_construct_id: backend_app_construct_ids) {
					try {
						constructs.add(backend_app_construct_id);
					} catch (IllegalArgumentException e) {
						BackendConnector.log.error("Error while transforming backend to client entity: " + e.getMessage(), e);
					}
				}
				cacheAppConstructs.put(_app, constructs);
				BackendConnector.log.info("[" + backend_app_construct_ids.length + "] app constructs received from backend, [" + constructs.size() + "] transformed to client representation");
			}
		}
		return cacheAppConstructs.get(_app);
	}

	/**
	 * Retrieves the change lists of all bugs relevant for the given application from the backend.
	 * @param _app
	 * @return
	 * @throws BackendConnectionException
	 */
	public Map<String, Set<ConstructId>> getAppBugs(GoalContext _ctx, Application _app) throws BackendConnectionException {
		// Make request and put in cache
		if(!this.cacheBugChangeLists.containsKey(_app)) {

			boolean app_exists = this.isAppExisting(_ctx, _app);
			final Map<String, Set<ConstructId>> changes = new HashMap<String, Set<ConstructId>>();
			int construct_count = 0;
			final Map<String,String> params = new HashMap<String,String>();
			params.put("historical", "false");
			if(app_exists) {
				final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.appBugs(_app), params)
						.setGoalContext(_ctx)
						.send()
						.getBody();
				final Bug[] bugs = (Bug[])JacksonUtil.asObject(json, Bug[].class);
				Set<ConstructId> changes_set = null;
				ConstructId json_cid = null;
				for(Bug b: bugs) {

					// Get the change list for the current bug id (or create it)
					changes_set = changes.get(b.getBugId());
					if(changes_set==null) {
						changes_set = new HashSet<ConstructId>();
						changes.put(b.getBugId(), changes_set);
					}

					// Add constructs
					for(ConstructChange cc: b.getConstructChanges()) {
						json_cid = cc.getConstructId();
						if(json_cid.getLang().equals(ProgrammingLanguage.JAVA) && !cc.getConstructChangeType().equals(ConstructChangeType.ADD)) {
							changes_set.add(json_cid);
						}					
					}

					// Total number of constructs received from the backend
					construct_count += changes_set.size();
				}
			}
			BackendConnector.log.info("[" + construct_count + "] constructs for [" + changes.keySet().size() + "] bugs received from backend");

			// Put in cache
			this.cacheBugChangeLists.put(_app, changes);
		}

		// Return from cache
		return this.cacheBugChangeLists.get(_app);
	}

	/**
	 * Retrieves the change lists of the given bug(s) from the backend. Note that only bugs relevant for the given application
	 * are included.
	 * @param _app
	 * @param _filter Comma-separated list of bug identifiers
	 * @return
	 * @throws BackendConnectionException
	 */
	public Map<String, Set<ConstructId>> getAppBugs(GoalContext _ctx, Application _app, String _filter) throws BackendConnectionException {
		// Return all change lists
		if(_filter==null || _filter.equals("")) {
			return this.getAppBugs(_ctx, _app);
		}
		// Filter
		else {
			final Map<String, Set<ConstructId>> all_change_lists = this.getAppBugs(_ctx, _app);
			final Map<String, Set<ConstructId>> filtered_change_list = new HashMap<String,Set<ConstructId>>();

			// Build filter
			final StringList filter = new StringList();
			filter.addAll(_filter, ",", true);

			// Filter
			for(Map.Entry<String, Set<ConstructId>> entry: all_change_lists.entrySet())
				if(filter.contains(entry.getKey(), ComparisonMode.EQUALS, CaseSensitivity.CASE_INSENSITIVE))
					filtered_change_list.put(entry.getKey(), entry.getValue());

			return filtered_change_list;
		}
	}

	/*public VulnerableDependency[] getVulnerableAppArchiveConstructs(Application _app) throws BackendConnectionException {
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.GET, PathBuilder.vulnArchiveConstructs(_app), null);
		final VulnerableDependency[] vulndeps = (VulnerableDependency[])JacksonUtil.asObject(req.send().getBody(), VulnerableDependency[].class);
		return vulndeps;
	}*/

	/*public VulnerableDependency[] getVulnerableDependencies(Application _app) throws BackendConnectionException {
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.GET, PathBuilder.vulnArchiveConstructs(_app), null);
		final VulnerableDependency[] vulndeps = (VulnerableDependency[])JacksonUtil.asObject(req.send().getBody(), VulnerableDependency[].class);
		return vulndeps;
	}*/

	/*public VulnerableDependency getVulnerableAppArchiveDependencyConstructs(Application _app, String _sha1, String _bugId) throws BackendConnectionException{
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.GET,PathBuilder.vulnerableDependencyConstructs(_app, _sha1, _bugId), null);
		VulnerableDependency vd = (VulnerableDependency)JacksonUtil.asObject(req.send().getBody(), VulnerableDependency.class);
		return vd;
	}*/

	public Set<Dependency> getAppDeps(GoalContext _ctx, Application _app) throws BackendConnectionException {
		if(!cacheAppDependencies.containsKey(_app)) {
			final Set<Dependency> deps = new HashSet<Dependency>();
			final boolean app_exists = this.isAppExisting(_ctx, _app);
			if(app_exists) {
				final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.appDeps(_app), null)
						.setGoalContext(_ctx)
						.send()
						.getBody();
				final Dependency[] deps_array = (Dependency[])JacksonUtil.asObject(json, Dependency[].class);
				deps.addAll(Arrays.asList(deps_array));
			}
			cacheAppDependencies.put(_app, deps);
			BackendConnector.log.info("[" + deps.size() + "] dependencies for app " + _app + " received from backend");
		}
		return cacheAppDependencies.get(_app);
	}

	public Set<VulnerableDependency> getAppVulnDeps(GoalContext _ctx, Application _app) throws BackendConnectionException {
		final Set<VulnerableDependency> vuln_deps = new HashSet<VulnerableDependency>();
		final boolean app_exists = this.isAppExisting(_ctx, _app);
		if(app_exists) {
			final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.appVulnDeps(_app), null)
					.setGoalContext(_ctx)
					.send()
					.getBody();
			final VulnerableDependency[] vuln_deps_array = (VulnerableDependency[])JacksonUtil.asObject(json, VulnerableDependency[].class);
			vuln_deps.addAll(Arrays.asList(vuln_deps_array));
		}
		BackendConnector.log.info("[" + vuln_deps.size() + "] vulnerable dependencies for app " + _app + " received from backend");
		return vuln_deps;
	}

	public VulnerableDependency[] getVulnDeps(Boolean unconfirmedOnly) throws BackendConnectionException {
		final Map<String,String> params = new HashMap<String,String>();
		params.put("unconfirmedOnly", unconfirmedOnly.toString());
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.GET, PathBuilder.vulnDeps(), params);
		final VulnerableDependency[] vulndeps = (VulnerableDependency[])JacksonUtil.asObject(req.send().getBody(), VulnerableDependency[].class);
		return vulndeps;
	}

	/**
	 * The entry points will be all the traces that have been collected during application tests,
	 * provided by the central Vulas engine.
	 * @param _sha1
	 */
	public Set<ConstructId> getAppTraces(GoalContext _ctx, @NotNull Application _app) throws BackendConnectionException {
		boolean app_exists = this.isAppExisting(_ctx, _app);
		final Set<ConstructId> constructs = new HashSet<ConstructId>();
		if(app_exists) {
			final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.appTraces(_app), null).setGoalContext(_ctx).send().getBody();
			final com.sap.psr.vulas.shared.json.model.Trace[] backend_traces = (com.sap.psr.vulas.shared.json.model.Trace[])JacksonUtil.asObject(json, com.sap.psr.vulas.shared.json.model.Trace[].class);
			for(com.sap.psr.vulas.shared.json.model.Trace backend_trace: backend_traces) {
				try {
					constructs.add(backend_trace.getConstructId());
				} catch (IllegalArgumentException e) {
					BackendConnector.log.error("Error while transforming backend to client entity: " + e.getMessage(), e);
				}
			}
			BackendConnector.log.info("[" + backend_traces.length + "] traces received from backend, [" + constructs.size() + "] transformed to client representation");
		}
		return constructs;
	}

	private static final Pattern pattern = Pattern.compile("\\\"countTotal\\\"\\s*:\\s*([\\d]*)");

	// ---------------------------------- LIB-RELATED CALLS
	
	public String getLibrary(String _sha1) throws EntityNotFoundInBackendException {
		HttpResponse response = null;
		try {
			response = new BasicHttpRequest(HttpMethod.GET, PathBuilder.lib(_sha1), null).send();
			if(response.isNotFound())
				throw new EntityNotFoundInBackendException("Library with SHA1 [" + _sha1 + "] not found in backend");
			else
				return response.getBody();
		} catch (BackendConnectionException e) {
			throw new EntityNotFoundInBackendException("Library with SHA1 [" + _sha1 + "] not found in backend");
		}

	}
	
	public int countLibraryConstructs(String _ja) throws BackendConnectionException {
		int count_existing = -1;
		String http_response = null;
		try {
			http_response  = this.getLibrary(_ja);

			// Use pattern matching to read number of constructs (rather than using JayWay JSonPath)
			final Matcher m = pattern.matcher(http_response);
			if(m.find()) count_existing = Integer.parseInt(m.group(1));
		}
		catch(NumberFormatException e) {
			final BackendConnectionException bce = new BackendConnectionException("Expected number at JSON property $.countTotal", e);
			bce.setHttpResponseBody(http_response);
			throw bce;
		}
		catch(EntityNotFoundInBackendException e) {
			count_existing = -1;
		}
		return count_existing;
	}

	public synchronized void uploadLibrary(Library _lib) throws BackendConnectionException {
		this.uploadLibraryRequest(_lib);
		//this.uploadLibraryRequest(_lib.getDigest(), JacksonUtil.asJsonString(_lib), (_lib.getConstructs()==null ? 0 : _lib.getConstructs().size()));
	}
	
	private void uploadLibraryRequest(Library _lib) throws BackendConnectionException {
	//private void uploadLibraryRequest(String _sha1, String _json, int constructs_count) throws BackendConnectionException {

		final String sha1 = _lib.getDigest();
		final String json = JacksonUtil.asJsonString(_lib);
		// Override setting
		final boolean override = VulasConfiguration.getGlobal().getConfiguration().getBoolean("collector.overrideArchive", false);
		
		final HttpRequestList req_list = new HttpRequestList();
		final BasicHttpRequest cond_req = new BasicHttpRequest(HttpMethod.GET, PathBuilder.lib(sha1), null);

		final Map<String,String> params = new HashMap<String,String>();
		params.put("skipResponseBody", "true");

		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.POST, PathBuilder.libs(), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_NOT_FOUND))
				.setPayload(json, null, false)
				);
		if (override){
			BackendConnector.log.info("collector.overrideArchive is enabled");
			req_list.addRequest(
					new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.lib(sha1), params)
					.setConditionRequest(cond_req)
					.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
					.setPayload(json, null, false)
					);
		}
		else if(!VulasConfiguration.getGlobal().getConfiguration().getBoolean("skipKnownArchive", false)){
			req_list.addRequest(
					new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.lib(sha1), params)
					.setConditionRequest(cond_req)
					.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
					.addCondition(new PutLibraryCondition(_lib))
					.setPayload(json, null, false)
					);
			
//			req_list.addRequest(
//					new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.lib(sha1), params)
//					.setConditionRequest(cond_req)
//					.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
//					.addCondition(new ContentCondition("\\\"countTotal\\\"\\s*:\\s*([\\d]*)", ContentCondition.Mode.LT_DOUBLE, new Integer(constructs_count).toString()))
//					.setPayload(json, null, false)
//					);
//			
//			
//			req_list.addRequest(
//					new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.lib(sha1), params)
//					.setConditionRequest(cond_req)
//					.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
//					.addCondition(new ContentCondition("\\\"libraryId\\\"\\s*:\\s*([a-z]*)", ContentCondition.Mode.EQ_STRING, "null"))
//					.setPayload(_json, null, false)
//					);
		}

		req_list.send();
	}

	public void uploadLibraryFile(String _sha1, Path _file) throws BackendConnectionException {
		try {
			final FileInputStream inputStream = new FileInputStream(_file.toFile());
			final HttpRequestList req_list = new HttpRequestList();
			final BasicHttpRequest cond_req = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.libupload(_sha1));
			final Map<String,String> params = new HashMap<String,String>();
			params.put("skipResponseBody", "true");
			req_list.addRequest(
					new ConditionalHttpRequest(HttpMethod.POST, PathBuilder.libupload(_sha1), params)
					.setConditionRequest(cond_req)
					.addCondition(new StatusCondition(HttpURLConnection.HTTP_NOT_FOUND))
					//	.addCondition(new ContentCondition("\\\"wellknownSha1\\\"\\s*:\\s*([a-zA-Z]*)", ContentCondition.Mode.EQ_STRING, "true"))
					.setBinPayload(inputStream, "application/octet-stream")
					);
			req_list.send();
		} catch (FileNotFoundException e) {
			BackendConnector.log.error("Cannot find [" + _file.toString()+ "]: Check if unknown to maven and upload will be skipped");
		} 
	}
	
	// ==================== Others
	
	public void uploadGoalExecution(GoalContext _ctx, String _json) throws BackendConnectionException {
		//TODO: Allow saving of workspace-specific goal executions (e.g., cleanspace)
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.goalExcecutions(null, _ctx.getSpace(), _ctx.getApplication()));
		req.setGoalContext(_ctx);
		req.setPayload(_json,  null,  true);
		req.send();
	}

	public void uploadTraces(GoalContext _ctx, Application _app, String _json) throws BackendConnectionException {
		if(this.isAppExisting(_ctx, _app)) {
			final Map<String,String> params = new HashMap<String,String>();
			params.put("skipResponseBody", "true");
			final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.appTraces(_app), params);
			req.setGoalContext(_ctx);
			req.setPayload(_json,  null,  false);
			req.send();
		}
		else {
			BackendConnector.log.warn("App " + _app + " does not exist in backend, trace upload skipped");
		}
	}

	public void uploadPaths(GoalContext _ctx, Application _app, String _json) throws BackendConnectionException {
		if(this.isAppExisting(_ctx, _app)) {			
			final Map<String,String> params = new HashMap<String,String>();
			params.put("skipResponseBody", "true");
			final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.appPaths(_app), params);
			req.setGoalContext(_ctx);
			req.setPayload(_json,  null,  false);
			req.send();
		}
		else {
			BackendConnector.log.warn("App " + _app + " does not exist in backend, path upload skipped");
		}
	}
	
	public boolean isBugExisting(String _bug) throws BackendConnectionException {
		final HttpResponse response = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.bug(_bug), null).send();
		return response.isOk();
	}

	public void uploadChangeList(String _bug, String _json) throws BackendConnectionException {
		
		// The request depending on whose result either POST or PUT will be called
		final BasicHttpRequest cond_req = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.bug(_bug), null);

		final HttpRequestList req_list = new HttpRequestList();
		final Map<String,String> params = new HashMap<String,String>();
		params.put("skipResponseBody", "true");
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.POST, PathBuilder.bugs(), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_NOT_FOUND))
				.setPayload(_json, null, false)
				);
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.bug(_bug), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
				.setPayload(_json, null, false)
				);
		req_list.send();
	}

	public void uploadAffectedLibs(String _bugid, String _json) throws BackendConnectionException {
		final Map<String,String> params = new HashMap<String,String>();
		params.put("source", "PRE_COMMIT_POM");		
		final BasicHttpRequest req = new BasicHttpRequest(HttpMethod.POST, PathBuilder.bugAffectedLibs(_bugid), params);
		req.setPayload(_json,  null,  true);
		req.send();
	}

	public void uploadCheckVersionResults(String _bugId, String _json) throws BackendConnectionException {
		final HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", "CHECK_VERSION");
		final HttpRequestList req_list = new HttpRequestList();
		final BasicHttpRequest cond_req = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.bugAffectedLibs(_bugId), params);
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.POST, PathBuilder.bugAffectedLibs(_bugId), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_NOT_FOUND))
				.setPayload(_json, null , false)

				);
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.bugAffectedLibs(_bugId), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
				.setPayload(_json, null , false)
				);

		req_list.send();
	}

	public AffectedLibrary[] getBugAffectedLibraries(String _bugId, String _source) throws BackendConnectionException {
		final HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", _source);
		final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.bugAffectedLibs(_bugId), params).send().getBody();
		return (AffectedLibrary[])JacksonUtil.asObject(json, AffectedLibrary[].class);
	}

	public void deletePatchEvalResults(String _bugId, String _source) throws BackendConnectionException {
		final HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", _source);
		final BasicHttpRequest del_req = new BasicHttpRequest(HttpMethod.DELETE, PathBuilder.bugAffectedLibs(_bugId), params);
		// payload cannot be empty otherwise request doesn t work
		del_req.setPayload("[]", "application/json", true);
		del_req.send();
	}

	public void uploadPatchEvalResults(String _bugId, String _json, String _source) throws BackendConnectionException {
		final HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", _source);

		final HttpRequestList req_list = new HttpRequestList();


		final BasicHttpRequest cond_req = new BasicHttpRequest(HttpMethod.OPTIONS, PathBuilder.bugAffectedLibs(_bugId), params);
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.POST, PathBuilder.bugAffectedLibs(_bugId), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_NOT_FOUND))
				.setPayload(_json, null , false)

				);
		req_list.addRequest(
				new ConditionalHttpRequest(HttpMethod.PUT, PathBuilder.bugAffectedLibs(_bugId), params)
				.setConditionRequest(cond_req)
				.addCondition(new StatusCondition(HttpURLConnection.HTTP_OK))
				.setPayload(_json, null , false)
				);

		req_list.send();

	}


	/**
	 * Loads all upload requests form the upload folder and 
	 */
	public void batchUpload() {
		final FileSearch fs = new FileSearch(new String[] { "obj" });
		final Set<Path> objs = fs.search(VulasConfiguration.getGlobal().getDir(CoreConfiguration.UPLOAD_DIR));
		for(Path obj: objs) {
			HttpRequest ur = null;
			try {
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(obj.toFile()));
				ur = (HttpRequest)ois.readObject();
				ois.close();
				ur.send();
			} catch (Exception e) {
				BackendConnector.log.error("Exception during batch upload of [" + obj + "] to [" + ur + "]: " + e.getMessage());
			}	
		}
	}

	/*public Signature getConstructSignature(Application _lib, ConstructId _cid) {
		Signature ast = null;
		try {
			final HttpResponse response = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.constructSignature(_lib, JavaId.toSharedType(_cid)), null).send();
			if(response.isOk()) {
				final String json = response.getBody();
				final Gson gson = GsonHelper.getCustomGsonBuilder().create();
				ast = gson.fromJson(json, ASTConstructBodySignature.class);
			} else {
				BackendConnector.log.error("HTTP response status [" + response.getStatus() + "], no AST for GAV [" + _lib + "] and construct " + _cid + " found");	
			}
		} catch (BackendConnectionException e1) {
			BackendConnector.log.error("Error while retrieving AST for GAV [" + _lib + "] and construct " + _cid + ": " + e1.getMessage(), e1);
		}
		return ast;
	}

	public Signature getConstructSignature(String _sha1, ConstructId _cid) {
		Signature ast = null;
		try {
			final HttpResponse response = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.constructSignature(_sha1, JavaId.toSharedType(_cid)), null).send();
			if(response.isOk()) {
				final String json = response.getBody();
				final Gson gson = GsonHelper.getCustomGsonBuilder().create();
				ast = gson.fromJson(json, ASTConstructBodySignature.class);
			} else {
				BackendConnector.log.error("HTTP response status [" + response.getStatus() + "], no AST for SHA1 [" + _sha1 + "] and construct " + _cid + " found");	
			}
		} catch (BackendConnectionException e1) {
			BackendConnector.log.error("Error while retrieving AST for SHA1 [" + _sha1 + "] and construct " + _cid + ": " + e1.getMessage(), e1);
		}
		return ast;
	}*/

	/**
	 * 
	 * @param _bugId
	 * @return
	 * @throws BackendConnectionException 
	 */
	public BugChangeList getBug(String _bugId) throws BackendConnectionException {
		HttpResponse r = new BasicHttpRequest(HttpMethod.GET, PathBuilder.bug(_bugId), null).send();
		final String json = r.getBody();
		if(r.getStatus()==200){
			final BugChangeList bugChangeList = (BugChangeList)JacksonUtil.asObject(json, BugChangeList.class);
			BackendConnector.log.info("bug change list for bug " + _bugId + " received from backend");
			return bugChangeList;
		}
		else
			return null;
	}

	/**
	 * 
	 * @param _className
	 * @return 
	 */
	public String getClassLibraryIds(String _className){
		String json = null;
		try {
			final HttpResponse response = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.classesLibraryIds(_className), null).send();
			if ( response.isOk() ){
				json = response.getBody();
			} else { 
				log.info(String.valueOf(response.getStatus()));
			}
		} catch (BackendConnectionException ex) {
			log.info(ex);
		}
		return json;
	}

	public synchronized String getAstForQnameInLib(String qString, Boolean _sources,ProgrammingLanguage _lang) {
		String json = null;
		try {
			final HttpResponse response = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.astForQnameInLib(qString, _sources,_lang), null).send();
			if ( response.isOk() ){
				json = response.getBody();
			} else {
				log.info(String.valueOf(response.getStatus()));
			}
		} catch (BackendConnectionException ex) {
			log.info(ex);
		}
		return json;
	}

	public synchronized String getSourcesForQnameInLib(String qString) {
		String json = null;
		try {
			final HttpResponse response = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.sourcesForQnameInLib(qString), null).send();
			if ( response.isOk() ){
				json = response.getBody();
			} 
		} catch (BackendConnectionException ex) {
			log.info(ex);
		}
		return json;
	}

	public synchronized ConstructId[] getArtifactBugConstructsIntersection(String _qString,List<ConstructId> c, String packaging) throws BackendConnectionException{
		String json = null;
		BasicHttpRequest bhr = new BasicHttpRequest(Service.CIA, HttpMethod.POST, PathBuilder.libConstructIdsIntersect(_qString,packaging), null);
		bhr.setPayload(JacksonUtil.asJsonString(c), "application/json", false);                
		final HttpResponse response = bhr.send();
		ConstructId[] intersection = null;
		if ( response.isOk() ){
			json = response.getBody();
			intersection= (ConstructId[])JacksonUtil.asObject(json, ConstructId[].class);
		} 
		return intersection;
	}

	public String getJarConstructs(String qString) {
		String json = null;
		try {
			final HttpResponse response = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.sourcesForQnameInLib(qString), null).send();
			if ( response.isOk() ){
				json = response.getBody();
			} 
		} catch (BackendConnectionException ex) {
			log.info(ex);
		}
		return json;
	}

	public synchronized String getAstDiff(String jsonReq) {
		String json = null;
		String ast =null;
		try {
			BasicHttpRequest bhr = new BasicHttpRequest(Service.CIA, HttpMethod.POST, PathBuilder.constructsDiff(), null);
			bhr.setPayload(jsonReq, "application/json", false);                
			final HttpResponse response = bhr.send();

			if ( response.isOk() ){
				json = response.getBody();
		//		ast = (String)JacksonUtil.asObject(json, String.class);
			} 
		} catch (BackendConnectionException ex) {
			log.error(ex);
		}
		return json;
	}

	public Library[] getBugLibraries(String _bugId) throws BackendConnectionException{
		final String json = new BasicHttpRequest(HttpMethod.GET, PathBuilder.bugLibraryVersions(_bugId), null).send().getBody();
		final Library[] libs = (Library[])JacksonUtil.asObject(json, Library[].class);
		BackendConnector.log.info("Libraries for bug " + _bugId + " received from backend");
		return libs;
	}

	public Artifact[] getAllArtifactsGroupArtifact(String _g, String _a) throws BackendConnectionException{
		String json = null;
		Artifact[] result = null;
		
		json = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.artifactsGroupVersion(_g,_a), null).send().getBody();
		BackendConnector.log.info("artifacts for  " + _g + ":" + _a + " received from backend");
		if(json!=null)
			result = (Artifact[])JacksonUtil.asObject(json, Artifact[].class);
		
		return result;
	}
	
	public Artifact getArtifact(String _g, String _a, String _v) throws BackendConnectionException{
		String json = null;
		Artifact result = null;
		
		json = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.artifactsGAV(_g,_a, _v), null).send().getBody();
		if(json!=null)
			result = (Artifact)JacksonUtil.asObject(json, Artifact.class);
		
		return result;
	}


	public synchronized ConstructId[] getArtifactConstructs(String _g, String _a, String _v) throws BackendConnectionException{
		final String json = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.artifactsConstruct(_g,_a,_v), null).send().getBody();
		return (ConstructId[])JacksonUtil.asObject(json, ConstructId[].class);
		//return (ConstructId[])JacksonUtil.asObject(new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.artifactsConstruct(_g,_a,_v), null).send().getBody(), ConstructId[].class);
	}


	public synchronized boolean doesArtifactExist(String _g, String _a, String _v,Boolean _sources, String packaging) throws InterruptedException, BackendConnectionException {
		final Map<String,String> params = new HashMap<String,String>();
		
		if(_sources!=null && _sources)
			params.put("classifier", "sources");
		params.put("packaging", packaging);

		params.put("skipResponseBody","true");
		
		final HttpResponse r = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.artifactsGAV(_g, _a, _v), params).send();
		if(r.getStatus()==HttpStatus.SC_OK)
			return true;
		else if (r.getStatus()==HttpURLConnection.HTTP_NOT_FOUND)
			return false;
		else {
			log.warn("Got status code [" + r.getStatus() + "], artifact [" + _g + ":" + _a + ":" + _v + "] is considered not being available");
			return false;
		}
	}

	public String getBugsList() throws BackendConnectionException {
		final String json = new BasicHttpRequest(Service.BACKEND, HttpMethod.GET, PathBuilder.bugs(), null).send().getBody();
		return json;
	}


	public HttpResponse getJarForLib(String _g,String _a,String _v,Boolean _s, String _d) throws BackendConnectionException{
		BasicHttpRequest b = new BasicHttpRequest(Service.CIA, HttpMethod.GET, PathBuilder.downloadArtifactJars(_g,_a,_v,_s), null);
		b.setDir(_d);
		return b.send();
	}

	public String getBugsForLib(String _digest) throws BackendConnectionException {
		final String json = new BasicHttpRequest(Service.BACKEND, HttpMethod.GET, PathBuilder.libbugs(_digest), null).send().getBody();
		return json;
	}

}