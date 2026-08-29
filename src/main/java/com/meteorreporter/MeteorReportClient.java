package com.meteorreporter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
class MeteorReportClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Type REPORT_LIST = new TypeToken<List<MeteorReport>>() { }.getType();
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final MeteorReporterConfig config;

	@Inject
	MeteorReportClient(OkHttpClient httpClient, Gson gson, MeteorReporterConfig config)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.config = config;
	}

	void list(Consumer<List<MeteorReport>> success, Consumer<String> failure)
	{
		HttpUrl url = reportsUrl();
		if (url == null)
		{
			failure.accept("Invalid API URL");
			return;
		}
		httpClient.newCall(requestBuilder(url).get().build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				log.debug("Unable to refresh meteor reports", exception);
				failure.accept("Report server unavailable");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						failure.accept("Report server returned " + response.code());
						return;
					}
					List<MeteorReport> reports = gson.fromJson(response.body().charStream(), REPORT_LIST);
					success.accept(reports == null ? Collections.emptyList() : reports);
				}
				catch (RuntimeException exception)
				{
					log.debug("Invalid meteor report response", exception);
					failure.accept("Invalid report response");
				}
			}
		});
	}

	void report(MeteorReport report, Runnable success, Consumer<String> failure)
	{
		HttpUrl url = reportsUrl();
		if (url == null)
		{
			failure.accept("Invalid API URL");
			return;
		}
		execute(requestBuilder(url).post(RequestBody.create(JSON, gson.toJson(report))).build(), success, failure);
	}

	void delete(MeteorReport report, Runnable success, Consumer<String> failure)
	{
		HttpUrl base = reportsUrl();
		if (base == null)
		{
			failure.accept("Invalid API URL");
			return;
		}
		HttpUrl url = base.newBuilder()
			.addPathSegment(Integer.toString(report.getWorld()))
			.addPathSegment(Integer.toString(report.getX()))
			.addPathSegment(Integer.toString(report.getY()))
			.addPathSegment(Integer.toString(report.getPlane()))
			.build();
		execute(requestBuilder(url).delete().build(), success, failure);
	}

	private void execute(Request request, Runnable success, Consumer<String> failure)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				log.debug("Meteor report request failed", exception);
				failure.accept("Report server unavailable");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (response.isSuccessful()) success.run();
					else failure.accept("Report server returned " + response.code());
				}
			}
		});
	}

	private Request.Builder requestBuilder(HttpUrl url)
	{
		Request.Builder builder = new Request.Builder().url(url);
		String key = config.sharedKey().trim();
		if (!key.isEmpty()) builder.header("X-Meteor-Key", key);
		return builder;
	}

	private HttpUrl reportsUrl()
	{
		String endpoint = config.apiEndpoint().trim().replaceAll("/+$", "");
		return HttpUrl.parse(endpoint + "/reports");
	}
}
