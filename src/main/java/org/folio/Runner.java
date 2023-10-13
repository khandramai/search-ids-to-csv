package org.folio;

import lombok.extern.log4j.Log4j2;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.folio.exception.RequestExecutionException;
import org.folio.handler.LoginResponseHandler;
import org.folio.handler.SaveIdsHandler;
import org.folio.handler.SearchJobHandler;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.folio.utils.Constants.CONTENT_TYPE;
import static org.folio.utils.Constants.X_OKAPI_TENANT;
import static org.folio.utils.Constants.X_OKAPI_TOKEN;

@Log4j2
public class Runner {

    public static void main(String[] args) throws IOException {

        String token;
        String jobId;

        Map<String, String> properties = Arrays.stream(args).map(arg -> arg.split("=")).collect(toMap(k -> k[0], v -> v[1]));

        var host = properties.get("-host");
        var tenant = properties.get("-tenant");
        var username = properties.get("-username");
        var password = properties.get("-password");
        var size = properties.get("-size");

        if (Objects.isNull(host) || Objects.isNull(tenant) || Objects.isNull(username) || Objects.isNull(password) || Objects.isNull(size)) {
            throw new RequestExecutionException("Invalid arguments line: " + String.join(" ", args));
        }

        var sizes = Arrays.stream(size.split(",")).map(Integer::parseInt).toList();

        final HttpPost login = new HttpPost(format("%s/bl-users/login?expandPermissions=true&fullPermissions=true", host));
        login.setHeader(X_OKAPI_TENANT, tenant);
        login.setHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType());
        login.setEntity(new StringEntity("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            token = client.execute(login, new LoginResponseHandler());
        } catch (IOException e) {
            throw new RequestExecutionException("Login error", e);
        }

        final HttpPost job = new HttpPost(format("%s/search/resources/jobs", host));
        job.setHeader(X_OKAPI_TENANT, tenant);
        job.setHeader(X_OKAPI_TOKEN, token);
        job.setHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType());
        job.setEntity(new StringEntity("{\"entityType\":\"INSTANCE\",\"query\":\"id=*\"}"));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
             jobId = client.execute(job, new SearchJobHandler());
        } catch (IOException e) {
            throw new RequestExecutionException("Job creating error", e);
        }

        final HttpGet idsRequest = new HttpGet(format("%s/search/resources/jobs/%s/ids", host, jobId));
        idsRequest.setHeader(X_OKAPI_TENANT, tenant);
        idsRequest.setHeader(X_OKAPI_TOKEN, token);
        idsRequest.setHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType());

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(idsRequest, new SaveIdsHandler(sizes));
        } catch (IOException e) {
            throw new RequestExecutionException("Retrieving instances ids error", e);
        }
    }
}