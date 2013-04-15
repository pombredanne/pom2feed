package net.zeroinstall.pom2feed.service;

import java.net.URL;
import org.junit.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.net.MalformedURLException;
import net.zeroinstall.model.InterfaceDocument;

public class FeedGeneratorTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);
    private FeedGenerator feedGenerator;

    public FeedGeneratorTest() throws MalformedURLException {
        this.feedGenerator = new FeedGenerator(
                new URL("http://localhost:8089/"),
                new URL("http://0install.de/maven/"));
    }

    @Test
    public void testLoad() throws Exception {
        stubFor(get(urlEqualTo("/group/artifact/maven-metadata.xml")).willReturn(aResponse().withStatus(200).
                withBody("<metadata>\n<groupId>group</groupId>\n<artifactId>artifact</artifactId>\n<versioning>\n<latest>1.1</latest>\n<versions>\n<version>1.0</version>\n<version>1.1</version>\n</versions>\n</versioning>\n</metadata>")));
        stubFor(get(urlEqualTo("/group/artifact/1.0/artifact-1.0.pom")).willReturn(aResponse().withStatus(200).
                withBody("<project><modelVersion>4.0.0</modelVersion><groupId>group</groupId><artifactId>artifact</artifactId><packaging>jar</packaging><version>1.0</version><name>Test Artifact</name></project>")));
        stubFor(head(urlEqualTo("/group/artifact/1.0/artifact-1.0.jar")).
                willReturn(aResponse().withStatus(200).withHeader("Content-Length", "1024")));
        stubFor(get(urlEqualTo("/group/artifact/1.0/artifact-1.0.jar.sha1")).
                willReturn(aResponse().withStatus(200).withBody("123abc")));
        stubFor(get(urlEqualTo("/group/artifact/1.1/artifact-1.1.pom")).willReturn(aResponse().withStatus(200).
                withBody("<project><modelVersion>4.0.0</modelVersion><groupId>group</groupId><artifactId>artifact</artifactId><packaging>jar</packaging><version>1.1</version><name>Test Artifact</name></project>")));
        stubFor(head(urlEqualTo("/group/artifact/1.1/artifact-1.1.jar")).
                willReturn(aResponse().withStatus(200).withHeader("Content-Length", "1024")));
        stubFor(get(urlEqualTo("/group/artifact/1.1/artifact-1.1.jar.sha1")).
                willReturn(aResponse().withStatus(200).withBody("123abc")));

        InterfaceDocument feed = InterfaceDocument.Factory.parse(feedGenerator.getFeed("group/artifact/"));

        verify(getRequestedFor(urlEqualTo("/group/artifact/maven-metadata.xml")));
        verify(getRequestedFor(urlEqualTo("/group/artifact/1.0/artifact-1.0.pom")));
        verify(headRequestedFor(urlEqualTo("/group/artifact/1.0/artifact-1.0.jar")));
        verify(getRequestedFor(urlEqualTo("/group/artifact/1.0/artifact-1.0.jar.sha1")));
        verify(getRequestedFor(urlEqualTo("/group/artifact/1.1/artifact-1.1.pom")));
        verify(headRequestedFor(urlEqualTo("/group/artifact/1.1/artifact-1.1.jar")));
        verify(getRequestedFor(urlEqualTo("/group/artifact/1.1/artifact-1.1.jar.sha1")));

    }
}
