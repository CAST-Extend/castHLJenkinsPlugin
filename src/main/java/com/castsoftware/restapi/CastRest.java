package com.castsoftware.restapi;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.castsoftware.util.CastUtil;
import com.castsoftware.restapi.pojo.Aad;
import com.castsoftware.restapi.pojo.Application;
import com.castsoftware.restapi.pojo.Metric;
import com.castsoftware.restapi.pojo.Module;
import com.castsoftware.restapi.pojo.Snapshot;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class CastRest {
	public static JsonResponse QueryAPI(String query) {
		ClientConfig clientConfig = new DefaultClientConfig();

		Client client = Client.create(clientConfig);

		WebResource webResource = client.resource(query);

		ClientResponse response = webResource.accept("application/json").type("application/json")
				.get(ClientResponse.class);

		return new JsonResponse(response.getStatus(), response.getEntity(String.class));
	}

	public static JsonResponse QueryAPI(String login, String password, String query) {
		ClientConfig clientConfig = new DefaultClientConfig();

		Client client = Client.create(clientConfig);

		client.addFilter(new HTTPBasicAuthFilter(login, password));

		WebResource webResource = client.resource(query);

		ClientResponse response = webResource.accept("application/json").type("application/json")
				.get(ClientResponse.class);

		return new JsonResponse(response.getStatus(), response.getEntity(String.class));
	}

	public static List<String> listApplications(String webServiceAddress) {
		JsonResponse jsonResponse = QueryAPI(String.format("%s/allApplications", webServiceAddress));
		List<String> apps = new ArrayList();

		if (jsonResponse.getCode() == 200) {
			Gson gson = new Gson();

			JsonResponse json = gson.fromJson(jsonResponse.getJsonString(), JsonResponse.class);

			String jsonString = json.getJsonString();

			Type listType = new TypeToken<List<String>>() {
			}.getType();
			apps = gson.fromJson(jsonString, listType);

		}
		return apps;
	}

	public static int analyizeApplications(String webServiceAddress, String applName) {
		JsonResponse jsonResponse = QueryAPI(String.format("%s/runHighlight?applName=%s", webServiceAddress, applName));
		int rslt = jsonResponse.getCode();
		Gson gson = new Gson();

		if (rslt == 200) {
			rslt = gson.fromJson(jsonResponse.getJsonString(), JsonResponse.class).getCode();
		} else {
			rslt *= -1;
		}
		return rslt;
	}

	public static int testConnection(String webServiceAddress) {
		JsonResponse jsonResponse = QueryAPI(String.format("%s/ping", webServiceAddress));

		if (jsonResponse.getCode() == 200) {
			Gson gson = new Gson();

			try {
				JsonResponse json = gson.fromJson(jsonResponse.getJsonString(), JsonResponse.class);
				if (!json.getJsonString().equals("pong")) {
					return -200;
				}
			} catch (JsonParseException e) {
				return -200;
			}
		}
		return jsonResponse.getCode();
	}

}
