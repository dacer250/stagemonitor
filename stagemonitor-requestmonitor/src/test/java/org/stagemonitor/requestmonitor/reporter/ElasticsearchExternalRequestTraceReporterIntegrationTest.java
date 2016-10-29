package org.stagemonitor.requestmonitor.reporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.uber.jaeger.reporters.LoggingReporter;
import com.uber.jaeger.samplers.ConstSampler;

import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.configuration.AbstractElasticsearchTest;
import org.stagemonitor.core.configuration.Configuration;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.core.util.IOUtils;
import org.stagemonitor.requestmonitor.RequestMonitorPlugin;

import java.util.concurrent.TimeUnit;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ElasticsearchExternalRequestTraceReporterIntegrationTest extends AbstractElasticsearchTest {

	protected ElasticsearchSpanReporter reporter;
	protected RequestMonitorPlugin requestMonitorPlugin;
	protected Configuration configuration;
	private com.uber.jaeger.Tracer tracer;

	@Before
	public void setUp() throws Exception {
		this.configuration = mock(Configuration.class);
		this.requestMonitorPlugin = mock(RequestMonitorPlugin.class);
		when(configuration.getConfig(CorePlugin.class)).thenReturn(corePlugin);
		when(configuration.getConfig(RequestMonitorPlugin.class)).thenReturn(requestMonitorPlugin);
		when(corePlugin.getElasticsearchClient()).thenReturn(elasticsearchClient);
		when(corePlugin.getMetricRegistry()).thenReturn(new Metric2Registry());
		when(requestMonitorPlugin.getOnlyReportNExternalRequestsPerMinute()).thenReturn(1000000d);
		reporter = new ElasticsearchSpanReporter();
		reporter.init(new SpanReporter.InitArguments(configuration));
		final String mappingTemplate = IOUtils.getResourceAsString("stagemonitor-elasticsearch-span-index-template.json");
		elasticsearchClient.sendMappingTemplateAsync(mappingTemplate, "stagemonitor-spans");
		elasticsearchClient.waitForCompletion();
		tracer = new com.uber.jaeger.Tracer.Builder("ElasticsearchExternalRequestTraceReporterIntegrationTest", new LoggingReporter(), new ConstSampler(true)).build();

	}

	@Test
	public void reportTemplateCreated() throws Exception {
		final JsonNode template = elasticsearchClient.getJson("/_template/stagemonitor-spans").get("stagemonitor-spans");
		assertEquals("stagemonitor-spans-*", template.get("template").asText());
		assertEquals(false, template.get("mappings").get("_default_").get("_all").get("enabled").asBoolean());
	}

	@Test
	public void reportSpan() throws Exception {
		reporter.report(new SpanReporter.ReportArguments(null, getSpan(100)));
		elasticsearchClient.waitForCompletion();
		refresh();
		final JsonNode hits = elasticsearchClient.getJson("/stagemonitor-spans*/_search").get("hits");
		assertEquals(1, hits.get("total").intValue());
		final JsonNode requestTraceJson = hits.get("hits").elements().next().get("_source");
		assertEquals("jdbc", requestTraceJson.get("type").asText());
		assertEquals("SELECT", requestTraceJson.get("method").asText());
		assertEquals(100000, requestTraceJson.get("duration").asInt());
		assertEquals("SELECT * from STAGEMONITOR where 1 < 2", requestTraceJson.get("request").asText());
		assertEquals("ElasticsearchExternalRequestTraceReporterIntegrationTest#test", requestTraceJson.get("name").asText());
	}

	private Span getSpan(long executionTimeMillis) {
		final Span span = tracer
				.buildSpan("ElasticsearchExternalRequestTraceReporterIntegrationTest#test")
				.withStartTimestamp(1)
				.start();
		Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
		span.setTag("type", "jdbc");
		span.setTag("method", "SELECT");
		span.setTag("request", "SELECT * from STAGEMONITOR where 1 < 2");
		Tags.PEER_SERVICE.set(span, "foo@jdbc:bar");
		span.finish(TimeUnit.MILLISECONDS.toMicros(executionTimeMillis) + 1);
		return span;
	}
}
