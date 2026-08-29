package com.meteorreporter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
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
	private static final HttpUrl API_URL = HttpUrl.get("https://meteors.cukservers.net/api/v1");
	private static final HttpUrl REPORTS_URL = API_URL.newBuilder().addPathSegment("reports").build();
	private static final HttpUrl SCOUTS_URL = API_URL.newBuilder().addPathSegment("scouts").build();
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Type REPORT_LIST = new TypeToken<List<MeteorReport>>() { }.getType();
	private static final Type SCOUT_LIST = new TypeToken<List<StarScout>>() { }.getType();
	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	MeteorReportClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	void list(Consumer<List<MeteorReport>> success, Consumer<String> failure)
	{
		list(false, success, failure);
	}

	/**
	 * @param fresh asks any cache between here and the server to fetch a new copy, for the moment
	 *              right after this client reported something.
	 */
	void list(boolean fresh, Consumer<List<MeteorReport>> success, Consumer<String> failure)
	{
		Request.Builder builder = new Request.Builder().url(REPORTS_URL).get();
		if (fresh) builder.header("Cache-Control", "no-cache");
		httpClient.newCall(builder.build()).enqueue(new Callback()
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

	void listScouts(Consumer<List<StarScout>> success, ObjIntConsumer<String> failure)
	{
		httpClient.newCall(new Request.Builder().url(SCOUTS_URL).get().build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				log.debug("Unable to refresh scouted stars", exception);
				failure.accept("Report server unavailable", -1);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						failure.accept("Report server returned " + response.code(), response.code());
						return;
					}
					List<StarScout> scouts = gson.fromJson(response.body().charStream(), SCOUT_LIST);
					success.accept(scouts == null ? Collections.emptyList() : scouts);
				}
				catch (RuntimeException exception)
				{
					log.debug("Invalid scouted star response", exception);
					failure.accept("Invalid report response", -1);
				}
			}
		});
	}

	void reportScout(StarScout scout, Runnable success, Consumer<String> failure)
	{
		Request request = new Request.Builder().url(SCOUTS_URL)
			.post(RequestBody.create(JSON, gson.toJson(scout))).build();
		execute(request, success, failure);
	}

	void report(MeteorReport report, Consumer<MeteorReportResponse> success, Consumer<String> failure)
	{
		Request request = new Request.Builder().url(REPORTS_URL)
			.post(RequestBody.create(JSON, gson.toJson(report))).build();
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
					if (!response.isSuccessful() || response.body() == null)
					{
						failure.accept("Report server returned " + response.code());
						return;
					}
					success.accept(gson.fromJson(response.body().charStream(), MeteorReportResponse.class));
				}
				catch (RuntimeException exception)
				{
					failure.accept("Invalid report response");
				}
			}
		});
	}

	void delete(MeteorReport report, Runnable success, Consumer<String> failure)
	{
		HttpUrl url = REPORTS_URL.newBuilder()
			.addPathSegment(Integer.toString(report.getWorld()))
			.addPathSegment(Integer.toString(report.getX()))
			.addPathSegment(Integer.toString(report.getY()))
			.addPathSegment(Integer.toString(report.getPlane()))
			.build();
		execute(new Request.Builder().url(url).delete().build(), success, failure);
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

}
